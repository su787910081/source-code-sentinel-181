package com.alibaba.csp.sentinel.demo.cluster.flow.rule;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.PropertyListener;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleUtil;
import com.alibaba.csp.sentinel.util.AssertUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClusterClientFlowRuleManager {

    // key: 资源名称
    private static Map<String, List<FlowRule>> clusterFlowRules = new ConcurrentHashMap<>();

    private static final ClusterFlowPropertyListener LISTENER = new ClusterFlowPropertyListener();
    private static SentinelProperty<List<FlowRule>> currentProperty = new DynamicSentinelProperty<>();

    static {
        currentProperty.addListener(LISTENER);
    }

    /**
     * Listen to the {@link SentinelProperty} for {@link FlowRule}s. The property is the source of {@link FlowRule}s.
     * Flow rules can also be set by {@link #loadRules(List)} directly.
     *
     * @param property the property to listen.
     */
    public static void register2Property(SentinelProperty<List<FlowRule>> property) {
        AssertUtil.notNull(property, "property cannot be null");
        synchronized (LISTENER) {
            RecordLog.info("[ClusterClientFlowRuleManager] Registering new property to cluster client flow rule manager");
            currentProperty.removeListener(LISTENER);
            property.addListener(LISTENER);
            currentProperty = property;
        }
    }

    /**
     * Load {@link FlowRule}s, former rules will be replaced.
     *
     * @param rules new rules to load.
     */
    public static void loadRules(List<FlowRule> rules) {
        currentProperty.updateValue(rules);
    }

    public static List<FlowRule> findRules(String resource) {
        return clusterFlowRules.get(resource);
    }

    private static final class ClusterFlowPropertyListener implements PropertyListener<List<FlowRule>> {

        @Override
        public void configUpdate(List<FlowRule> value) {
            Map<String, List<FlowRule>> rules = FlowRuleUtil.buildFlowRuleMap(value);
            //the rules was always not null, it's no need to check nullable
            //remove checking to avoid IDE warning
            updateClusterFlowRules(rules);
            RecordLog.info("[FlowRuleManager] Flow rules received: {}", rules);
        }

        @Override
        public void configLoad(List<FlowRule> conf) {
            Map<String, List<FlowRule>> rules = FlowRuleUtil.buildFlowRuleMap(conf);
            updateClusterFlowRules(rules);
            RecordLog.info("[FlowRuleManager] Flow rules loaded: {}", rules);
        }

        private void updateClusterFlowRules(Map<String, List<FlowRule>> rules) {
            if (rules == null) {
                clusterFlowRules = new ConcurrentHashMap<>();
                return;
            }

            clusterFlowRules = new ConcurrentHashMap<>(rules);
        }
    }
}
