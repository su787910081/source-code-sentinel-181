/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.demo.cluster.init;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.demo.cluster.DemoConstants;
import com.alibaba.csp.sentinel.demo.cluster.entity.ClusterGroupEntity;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.PropertyKeyConst;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Eric Zhao
 */
public class DemoClusterInitFunc implements InitFunc {

    private static final String APP_NAME = AppNameUtil.getAppName();

    private final Properties nacosProp = new Properties();
    private final String nacosRemoteAddress = "kwe-beta.huawei.com:80";
    private final String nacosNamespaceId = "suyh-local-nacos";
    private final String groupId = "DEFAULT_GROUP";

    private final String flowDataId = APP_NAME + DemoConstants.FLOW_POSTFIX;
    private final String paramDataId = APP_NAME + DemoConstants.PARAM_FLOW_POSTFIX;
    private final String configDataId = APP_NAME + "-cluster-client-config";
    private final String clusterMapDataId = APP_NAME + DemoConstants.CLUSTER_MAP_POSTFIX;

    @Override
    public void init() throws Exception {
        nacosProp.put(PropertyKeyConst.SERVER_ADDR, nacosRemoteAddress);
        nacosProp.put(PropertyKeyConst.NAMESPACE, nacosNamespaceId);

        // Register client dynamic rule data source.
        // suyh - 初始化规则，从nacos 加载初始化，并添加监听器。
        initDynamicRuleProperty();

        // Register token client related data source.
        // Token client common config:
        // client：加载ClusterClientConfig（requestTimeout）
        initClientConfigProperty();
        // Token client assign config (e.g. target token server) retrieved from assign map:
        // client：加载ClusterClientAssignConfig（serverHost、serverPort）
        // suyh - 这里只是从nacos 中加载初始化信息，并初始化ClusterClientConfigManager 对应的数据，最终使用是在DefaultClusterTokenClient 中。
        initClientServerAssignProperty();

        // Register token server related data source.
        // Register dynamic rule data source supplier for token server:
        // server：加载集群规则，namespace下对应的FlowRule
        registerClusterRuleSupplier();
        // Token server transport config extracted from assign map:
        // server：从assignMap中获取ServerTransportConfig（port、idleSeconds）
        initServerTransportConfigProperty();

        // Init cluster state property for extracting mode from cluster map data source.
        // 根据我们的clusterDemo-cluster-map配置，设置当前应用状态（CLIENT/SERVER/NOT_STARTED）
        initStateProperty();
    }

    private void initDynamicRuleProperty() {
        // suyh - 流控规则
        ReadableDataSource<String, List<FlowRule>> ruleSource = new NacosDataSource<>(nacosProp, groupId,
            flowDataId, source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
        FlowRuleManager.register2Property(ruleSource.getProperty());

        // suyh - 热点流控规则
        ReadableDataSource<String, List<ParamFlowRule>> paramRuleSource = new NacosDataSource<>(nacosProp, groupId,
            paramDataId, source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
        ParamFlowRuleManager.register2Property(paramRuleSource.getProperty());
    }

    // client端加载requestTimeout配置
    private void initClientConfigProperty() {
        ReadableDataSource<String, ClusterClientConfig> clientConfigDs = new NacosDataSource<>(nacosProp, groupId,
            configDataId, source -> JSON.parseObject(source, new TypeReference<ClusterClientConfig>() {}));
        ClusterClientConfigManager.registerClientConfigProperty(clientConfigDs.getProperty());
    }

    // server端加载Token Server 的监听端口
    private void initServerTransportConfigProperty() {
        ReadableDataSource<String, ServerTransportConfig> serverTransportDs = new NacosDataSource<>(nacosProp, groupId,
            clusterMapDataId, source -> {
            List<ClusterGroupEntity> groupList = JSON.parseObject(source, new TypeReference<List<ClusterGroupEntity>>() {});
            return Optional.ofNullable(groupList)
                // 主要在这里，通过clusterDemo-cluster-map配置的值中的machineID来比对当前应用IP@port是否符合，符合则代表是server端
                // 获取配置中的port值
                // suyh - 将ClusterGroupEntity 解析出来
                // suyh - 服务器匹配，校验属性machineId，如果当前进程与该值匹配，则认为当前进程为服务器，直接返回ServerTransportConfig 对象。
                // suyh - 否则返回 null
                .flatMap(this::extractServerTransportConfig)
                .orElse(null);
        });
        ClusterServerConfigManager.registerServerTransportProperty(serverTransportDs.getProperty());
    }

    // 这个是最关键的，根据namespace来动态从Nacos中获取FlowRule
    // namespace可以主动加载，通过代码ClusterServerConfigManager.loadServerNamespaceSet(Collections.singleton(APP_NAME));
    // 也可以不写，在启动项中添加project.name=xxx，则namespace默认取该配置项值
    private void registerClusterRuleSupplier() {
        // Register cluster flow rule property supplier which creates data source by namespace.
        // Flow rule dataId format: ${namespace}-flow-rules
        ClusterFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(nacosProp, groupId,
                namespace + DemoConstants.FLOW_POSTFIX, source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
            return ds.getProperty();
        });
        // Register cluster parameter flow rule property supplier which creates data source by namespace.
        ClusterParamFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<ParamFlowRule>> ds = new NacosDataSource<>(nacosProp, groupId,
                namespace + DemoConstants.PARAM_FLOW_POSTFIX, source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
            return ds.getProperty();
        });
    }

    // 这里主要是通过map配置项中的clientSet，比对当前应用的ip:port来确认当前是否client端，如果是，则设置serverIp:serverPort为配置中的ip:port
    private void initClientServerAssignProperty() {
        // Cluster map format:
        // [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
        // machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
        // suyh - 如果当前进程是客户端则最终解析出一个对象，如果当前进程是服务器则最终返回null。
        ReadableDataSource<String, ClusterClientAssignConfig> clientAssignDs = new NacosDataSource<>(nacosProp, groupId,
            clusterMapDataId, source -> {
            // suyh - nacos 中配置的值首先解析出来的是ClusterGroupEntity 对象。
            List<ClusterGroupEntity> groupList = JSON.parseObject(source, new TypeReference<List<ClusterGroupEntity>>() {});
            // suyh - 将ClusterGroupEntity 解析出来
            // suyh - 首先进行服务器校验的匹配，校验属性machineId，如果当前进程与该值匹配，则认为当前进程为服务器，直接返回null。
            // suyh - 否则继续匹配clientSet 中的值，同样以machineId 的方式进行匹配，如果匹配，则当前进程为客户端。
            // suyh - 如果匹配到当前进程为客户端，则返回一个ClusterClientAssignConfig 对象。
            return Optional.ofNullable(groupList)
                // 主要在这里
                .flatMap(this::extractClientAssignment)
                .orElse(null);
        });
        ClusterClientConfigManager.registerServerAssignProperty(clientAssignDs.getProperty());
    }

    // 这里同样很关键，通过map配置中提前设定好的clientSet，machineID来确定当前应用是server还是client
    private void initStateProperty() {
        // Cluster map format:
        // [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
        // machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
        ReadableDataSource<String, Integer> clusterModeDs = new NacosDataSource<>(nacosProp, groupId,
            clusterMapDataId, source -> {
            List<ClusterGroupEntity> groupList = JSON.parseObject(source, new TypeReference<List<ClusterGroupEntity>>() {});
            return Optional.ofNullable(groupList)
                // 主要在这里
                .map(this::extractMode)
                .orElse(ClusterStateManager.CLUSTER_NOT_STARTED);
        });
        ClusterStateManager.registerProperty(clusterModeDs.getProperty());
    }

    private int extractMode(List<ClusterGroupEntity> groupList) {
        // If any server group machineId matches current, then it's token server.
        if (groupList.stream().anyMatch(this::machineEqual)) {
            return ClusterStateManager.CLUSTER_SERVER;
        }
        // If current machine belongs to any of the token server group, then it's token client.
        // Otherwise it's unassigned, should be set to NOT_STARTED.
        boolean canBeClient = groupList.stream()
            .flatMap(e -> e.getClientSet().stream())
            .filter(Objects::nonNull)
            .anyMatch(e -> e.equals(getCurrentMachineId()));
        return canBeClient ? ClusterStateManager.CLUSTER_CLIENT : ClusterStateManager.CLUSTER_NOT_STARTED;
    }

    private Optional<ServerTransportConfig> extractServerTransportConfig(List<ClusterGroupEntity> groupList) {
        return groupList.stream()
            .filter(this::machineEqual)
            .findAny()
            .map(e -> new ServerTransportConfig().setPort(e.getPort()).setIdleSeconds(600));
    }

    private Optional<ClusterClientAssignConfig> extractClientAssignment(List<ClusterGroupEntity> groupList) {
        if (groupList.stream().anyMatch(this::machineEqual)) {
            return Optional.empty();
        }
        // Build client assign config from the client set of target server group.
        for (ClusterGroupEntity group : groupList) {
            if (group.getClientSet().contains(getCurrentMachineId())) {
                String ip = group.getIp();
                Integer port = group.getPort();
                return Optional.of(new ClusterClientAssignConfig(ip, port));
            }
        }
        return Optional.empty();
    }

    private boolean machineEqual(/*@Valid*/ ClusterGroupEntity group) {
        return getCurrentMachineId().equals(group.getMachineId());
    }

    private String getCurrentMachineId() {
        // Note: this may not work well for container-based env.
        return HostNameUtil.getIp() + SEPARATOR + TransportConfig.getRuntimePort();
    }

    private static final String SEPARATOR = "@";
}
