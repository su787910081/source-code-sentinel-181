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
package com.alibaba.csp.sentinel.slots.block.flow.param;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.statistic.cache.CacheMap;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rule checker for parameter flow control.
 *
 * @author Eric Zhao
 * @since 0.2.0
 */
public final class ParamFlowChecker {

    public static boolean passCheck(ResourceWrapper resourceWrapper, /*@Valid*/ ParamFlowRule rule, /*@Valid*/ int count,
                             Object... args) {
        if (args == null) {
            return true;
        }

        int paramIdx = rule.getParamIdx();
        if (args.length <= paramIdx) {
            return true;
        }

        // Get parameter value.
        Object value = args[paramIdx];

        // Assign value with the result of paramFlowKey method
        if (value instanceof ParamFlowArgument) {
            value = ((ParamFlowArgument) value).paramFlowKey();
        }
        // If value is null, then pass
        if (value == null) {
            return true;
        }

        if (rule.isClusterMode() && rule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
            return passClusterCheck(resourceWrapper, rule, count, value);
        }

        return passLocalCheck(resourceWrapper, rule, count, value);
    }

    private static boolean passLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count,
                                          Object value) {
        try {
            if (Collection.class.isAssignableFrom(value.getClass())) {
                for (Object param : ((Collection)value)) {
                    if (!passSingleValueCheck(resourceWrapper, rule, count, param)) {
                        return false;
                    }
                }
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Object param = Array.get(value, i);
                    if (!passSingleValueCheck(resourceWrapper, rule, count, param)) {
                        return false;
                    }
                }
            } else {
                return passSingleValueCheck(resourceWrapper, rule, count, value);
            }
        } catch (Throwable e) {
            RecordLog.warn("[ParamFlowChecker] Unexpected error", e);
        }

        return true;
    }

    static boolean passSingleValueCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount,
                                        Object value) {
        if (rule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
            if (rule.getControlBehavior() == RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER) {
                return passThrottleLocalCheck(resourceWrapper, rule, acquireCount, value);
            } else {
                return passDefaultLocalCheck(resourceWrapper, rule, acquireCount, value);
            }
        } else if (rule.getGrade() == RuleConstant.FLOW_GRADE_THREAD) {
            Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
            long threadCount = getParameterMetric(resourceWrapper).getThreadCount(rule.getParamIdx(), value);
            if (exclusionItems.contains(value)) {
                int itemThreshold = rule.getParsedHotItems().get(value);
                return ++threadCount <= itemThreshold;
            }
            long threshold = (long)rule.getCount();
            return ++threadCount <= threshold;
        }

        return true;
    }

    static boolean passDefaultLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount,
                                         Object value) {
        // suyh - 每个资源都有一个对应的热点参数统计对象
        ParameterMetric metric = getParameterMetric(resourceWrapper);
        CacheMap<Object, AtomicLong> tokenCounters = metric == null ? null : metric.getRuleTokenCounter(rule);
        CacheMap<Object, AtomicLong> timeCounters = metric == null ? null : metric.getRuleTimeCounter(rule);

        if (tokenCounters == null || timeCounters == null) {
            return true;
        }

        // Calculate max token count (threshold)
        Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
        // suyh - 默认限流阈值
        long tokenCount = (long)rule.getCount();
        if (exclusionItems.contains(value)) {
            // suyh - 指定参数值的限流阈值
            tokenCount = rule.getParsedHotItems().get(value);
        }

        if (tokenCount == 0) {
            return false;
        }

        long maxCount = tokenCount + rule.getBurstCount();
        if (acquireCount > maxCount) {
            return false;
        }

        while (true) {
            long currentTime = TimeUtil.currentTimeMillis();

            // suyh - 对于该资源的该参数值，如果是第一次访问，则创建对应的原子对象，保存当前时间
            // 开始时间控制到整秒，与统计时间窗口起始时间一致。
            long startTime = currentTime - currentTime % 1000;
            AtomicLong lastAddTokenTime = timeCounters.putIfAbsent(value, new AtomicLong(startTime));
            if (lastAddTokenTime == null) {
                // Token never added, just replenish the tokens and consume {@code acquireCount} immediately.
                // suyh - 因为是每一次访问，时间创建了，同步也需要创建令牌数。但是这里的令牌数会将该次请求的数量扣减掉。
                // suyh - 这里初始化的时候添加了burst count 的值
                tokenCounters.putIfAbsent(value, new AtomicLong(maxCount - acquireCount));
                return true;
            }

            // Calculate the time duration since last token was added.
            long passTime = currentTime - lastAddTokenTime.get();
            // A simplified token bucket algorithm that will replenish the tokens only when statistic window has passed.
            // suyh - 如果当前时间与最后一次的相距时长，超过了统计时长。则重置统计时间与令牌
            if (passTime > rule.getDurationInSec() * 1000) {
                AtomicLong oldQps = tokenCounters.putIfAbsent(value, new AtomicLong(maxCount - acquireCount));
                if (oldQps == null) {
                    // Might not be accurate here.
                    // suyh - 不应该出现跑到这里的情况
                    // 重置起始计时时刻
                    lastAddTokenTime.set(startTime);
                    return true;
                } else {
                    long restQps = oldQps.get();
                    // suyh - 在这个间隔时间内
                    long toAddCount = (passTime * tokenCount) / (rule.getDurationInSec() * 1000);
                    long newQps = toAddCount + restQps > maxCount ? (maxCount - acquireCount)
                        : (restQps + toAddCount - acquireCount);

                    if (newQps < 0) {
                        return false;
                    }
                    if (oldQps.compareAndSet(restQps, newQps)) {
                        // 重置起始计时时刻
                        lastAddTokenTime.set(startTime);
                        return true;
                    }
                    Thread.yield();
                }
            } else {
                // suyh - 在统计时长内，则判断是否还有足够令牌可取，若可以则取出令牌，否则限流(拒绝访问)。
                AtomicLong oldQps = tokenCounters.get(value);
                if (oldQps != null) {
                    long oldQpsValue = oldQps.get();
                    if (oldQpsValue - acquireCount >= 0) {
                        if (oldQps.compareAndSet(oldQpsValue, oldQpsValue - acquireCount)) {
                            return true;
                        }
                    } else {
                        return false;
                    }
                }
                Thread.yield();
            }
        }
    }

    static boolean passThrottleLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount,
                                          Object value) {
        ParameterMetric metric = getParameterMetric(resourceWrapper);
        CacheMap<Object, AtomicLong> timeRecorderMap = metric == null ? null : metric.getRuleTimeCounter(rule);
        if (timeRecorderMap == null) {
            return true;
        }

        // Calculate max token count (threshold)
        Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
        long tokenCount = (long)rule.getCount();
        if (exclusionItems.contains(value)) {
            tokenCount = rule.getParsedHotItems().get(value);
        }

        if (tokenCount == 0) {
            return false;
        }

        long costTime = Math.round(1.0 * 1000 * acquireCount * rule.getDurationInSec() / tokenCount);
        while (true) {
            long currentTime = TimeUtil.currentTimeMillis();
            // 开始时间控制到整秒，与统计时间窗口起始时间一致。
            long startTime = currentTime - currentTime % 1000;
            AtomicLong timeRecorder = timeRecorderMap.putIfAbsent(value, new AtomicLong(startTime));
            if (timeRecorder == null) {
                return true;
            }
            //AtomicLong timeRecorder = timeRecorderMap.get(value);
            long lastPassTime = timeRecorder.get();
            long expectedTime = lastPassTime + costTime;

            if (expectedTime <= currentTime || expectedTime - currentTime < rule.getMaxQueueingTimeMs()) {
                AtomicLong lastPastTimeRef = timeRecorderMap.get(value);
                // 更新下一次处理的时刻为期望的那个时刻
                if (lastPastTimeRef.compareAndSet(lastPassTime, expectedTime)) {
                    long waitTime = expectedTime - currentTime;
                    if (waitTime > 0) {
                        lastPastTimeRef.set(expectedTime);
                        try {
                            TimeUnit.MILLISECONDS.sleep(waitTime);
                        } catch (InterruptedException e) {
                            RecordLog.warn("passThrottleLocalCheck: wait interrupted", e);
                        }
                    }
                    return true;
                } else {
                    Thread.yield();
                }
            } else {
                return false;
            }
        }
    }

    private static ParameterMetric getParameterMetric(ResourceWrapper resourceWrapper) {
        // Should not be null.
        return ParameterMetricStorage.getParamMetric(resourceWrapper);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> toCollection(Object value) {
        if (value instanceof Collection) {
            return (Collection<Object>)value;
        } else if (value.getClass().isArray()) {
            List<Object> params = new ArrayList<Object>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object param = Array.get(value, i);
                params.add(param);
            }
            return params;
        } else {
            return Collections.singletonList(value);
        }
    }

    private static boolean passClusterCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count,
                                            Object value) {
        try {
            Collection<Object> params = toCollection(value);

            TokenService clusterService = pickClusterService();
            if (clusterService == null) {
                // No available cluster client or server, fallback to local or
                // pass in need.
                return fallbackToLocalOrPass(resourceWrapper, rule, count, params);
            }

            TokenResult result = clusterService.requestParamToken(rule.getClusterConfig().getFlowId(), count, params);
            switch (result.getStatus()) {
                case TokenResultStatus.OK:
                    return true;
                case TokenResultStatus.BLOCKED:
                    return false;
                default:
                    return fallbackToLocalOrPass(resourceWrapper, rule, count, params);
            }
        } catch (Throwable ex) {
            RecordLog.warn("[ParamFlowChecker] Request cluster token for parameter unexpected failed", ex);
            return fallbackToLocalOrPass(resourceWrapper, rule, count, value);
        }
    }

    private static boolean fallbackToLocalOrPass(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count,
                                                 Object value) {
        if (rule.getClusterConfig().isFallbackToLocalWhenFail()) {
            return passLocalCheck(resourceWrapper, rule, count, value);
        } else {
            // The rule won't be activated, just pass.
            return true;
        }
    }

    private static TokenService pickClusterService() {
        if (ClusterStateManager.isClient()) {
            return TokenClientProvider.getClient();
        }
        if (ClusterStateManager.isServer()) {
            return EmbeddedClusterTokenServerProvider.getServer();
        }
        return null;
    }

    private ParamFlowChecker() {
    }
}
