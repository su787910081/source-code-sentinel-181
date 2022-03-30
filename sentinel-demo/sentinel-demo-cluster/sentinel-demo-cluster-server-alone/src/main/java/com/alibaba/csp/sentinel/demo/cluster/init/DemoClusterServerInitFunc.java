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

import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.demo.cluster.DemoConstants;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.PropertyKeyConst;

import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author Eric Zhao
 */
public class DemoClusterServerInitFunc implements InitFunc {

    private final Properties nacosProp = new Properties();
    private final String nacosRemoteAddress = "localhost:8848";
    private final String nacosNamespaceId = "suyh-sentinel-alone";
    private final String groupId = "DEFAULT_GROUP";
    private final String namespaceSetDataId = "cluster-server-namespace-set";
    private final String serverTransportDataId = "cluster-server-transport-config";
    private final String serverFlowDataId = "cluster-server-flow-config";

    @Override
    public void init() throws Exception {
        nacosProp.put(PropertyKeyConst.SERVER_ADDR, nacosRemoteAddress);
        nacosProp.put(PropertyKeyConst.NAMESPACE, nacosNamespaceId);

        // Register cluster flow rule property supplier which creates data source by namespace.
        // suyh - 每一个客户端集群被列为一组namespace，而这个namespace 在TokenServer 中并没有使用。
        // suyh - 它只是可以将所有的客户端集群的流控都加载进来，并保存下来进行管理。
        // suyh - 所有的namespace 对应的集群流控的处理回调都使用这一个。它的功能是：当有新的namespace 注册时，为其在nacos 注册一个集群流规则的对应监听。
        ClusterFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(nacosProp, groupId,
                namespace + DemoConstants.CLUSTER_FLOW_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
            return ds.getProperty();
        });
        // Register cluster parameter flow rule property supplier.
        ClusterParamFlowRuleManager.setPropertySupplier(namespace -> {
            ReadableDataSource<String, List<ParamFlowRule>> ds = new NacosDataSource<>(nacosProp, groupId,
                namespace + DemoConstants.CLUSTER_PARAM_FLOW_POSTFIX,
                source -> JSON.parseObject(source, new TypeReference<List<ParamFlowRule>>() {}));
            return ds.getProperty();
        });

        // Server namespace set (scope) data source.
        ReadableDataSource<String, Set<String>> namespaceDs = new NacosDataSource<>(nacosProp, groupId,
            namespaceSetDataId, source -> JSON.parseObject(source, new TypeReference<Set<String>>() {}));
        ClusterServerConfigManager.registerNamespaceSetProperty(namespaceDs.getProperty());
        // Server transport configuration data source.
        ReadableDataSource<String, ServerTransportConfig> transportConfigDs = new NacosDataSource<>(nacosProp,
            groupId, serverTransportDataId,
            source -> JSON.parseObject(source, new TypeReference<ServerTransportConfig>() {}));
        ClusterServerConfigManager.registerServerTransportProperty(transportConfigDs.getProperty());
    }
}
