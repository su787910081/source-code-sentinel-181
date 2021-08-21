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
package com.suyh.csp.sentinel.demo.cluter.alone.client.init;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.demo.cluster.DemoConstants;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.csp.sentinel.demo.cluster.flow.rule.ClusterClientFlowRuleManager;

import java.util.List;
import java.util.Properties;

/**
 * @author Eric Zhao
 */
public class DemoClusterAloneClientInitFunc implements InitFunc {

    private static final String APP_NAME = AppNameUtil.getAppName();

    private final Properties nacosProp = new Properties();
    private final String groupId = "DEFAULT_GROUP";

    private final String clusterFlowDataId = APP_NAME + DemoConstants.CLUSTER_FLOW_POSTFIX;
    private final String clusterParamDataId = APP_NAME + DemoConstants.CLUSTER_PARAM_FLOW_POSTFIX;
    private final String configDataId = APP_NAME + "-cluster-client-config";
    private final String clusterMapDataId = APP_NAME + DemoConstants.CLUSTER_MAP_POSTFIX;

    public DemoClusterAloneClientInitFunc() {
        String nacosRemoteAddress = "localhost:8848";
        String nacosNamespaceId = "suyh-sentinel-alone";
        nacosProp.put(PropertyKeyConst.SERVER_ADDR, nacosRemoteAddress);
        nacosProp.put(PropertyKeyConst.NAMESPACE, nacosNamespaceId);
    }

    @Override
    public void init() throws Exception {

        // Register client dynamic rule data source.
        // suyh - 初始化规则，从nacos 加载初始化，并添加监听器。
        initDynamicRuleProperty();

        // suyh - 这个没啥，就是客户端的requestTimeout(就这一个属性)，具体干啥用的，看名字就是超时，没具体看。
        initClientConfigProperty();

        // suyh - 加载集群客户端访问的服务器IP:PORT
        initClientClientAssignProperty();

        // suyh - 下面这种方式不好，如果这样的话，在dashboard 上面会有一个null:18730的一个server id 出现。
        // 指定当前进程为集群的客户端模式,因为不需要将其从客户端变成服务端
        ClusterStateManager.applyState(ClusterStateManager.CLUSTER_CLIENT);
    }

    // suyh - 按理来说这个是本机流控规则的加载，但是看样子如果不加载的话好像无法请求集群流控的样子。
    private void initDynamicRuleProperty() {

        // suyh - 集群流控规则
        ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(nacosProp, groupId, clusterFlowDataId,
                source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
        ClusterClientFlowRuleManager.register2Property(ds.getProperty());

        // suyh - 集群热点流控规则
//        ClusterParamFlowRuleManager.setPropertySupplier(namespace -> {
//            ReadableDataSource<String, List<ParamFlowRule>> ds = new NacosDataSource<>(nacosProp, groupId, clusterParamDataId,
//                    source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
//            return ds.getProperty();
//        });
//        ClusterParamFlowRuleManager.register2Property(APP_NAME);
    }

    // client端加载requestTimeout配置
    private void initClientConfigProperty() {
        ReadableDataSource<String, ClusterClientConfig> clientConfigDs = new NacosDataSource<>(nacosProp, groupId,
            configDataId, source -> JSON.parseObject(source, new TypeReference<ClusterClientConfig>() {}));
        ClusterClientConfigManager.registerClientConfigProperty(clientConfigDs.getProperty());
    }

    // 这里主要是通过map配置项中的clientSet，比对当前应用的ip:port来确认当前是否client端，如果是，则设置serverIp:serverPort为配置中的ip:port
    private void initClientClientAssignProperty() {
        // Cluster map format:
        // [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
        // machineId: <ip@commandPort>, commandPort for port exposed to Sentinel dashboard (transport module)
        // suyh - 如果当前进程是客户端则最终解析出一个对象，如果当前进程是服务器则最终返回null。
        ReadableDataSource<String, ClusterClientAssignConfig> clientAssignDs = new NacosDataSource<>(
                nacosProp, groupId, clusterMapDataId,
                source -> JSON.parseObject(source, ClusterClientAssignConfig.class));
        ClusterClientConfigManager.registerServerAssignProperty(clientAssignDs.getProperty());
    }
}
