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
package com.alibaba.csp.sentinel;

/**
 * https://www.javadoop.com/post/sentinel
 * 第二个参数标识资源的类型，我们左边的代码使用了 EntryType.IN 代表这个是入口流量，比如我们的接口对外提供服务，那么我们通常就是控制入口流量；
 * EntryType.OUT 代表出口流量，比如上面的 getOrderInfo 方法（没写默认就是 OUT），它的业务需要调用订单服务，像这种情况，压力其实都在订单服务中，那么我们就指定它为出口流量。
 * 这个流量类型有什么用呢？答案在 SystemSlot 类中，它用于实现自适应限流，根据系统健康状态来判断是否要限流，如果是 OUT 类型，由于压力在外部系统中，所以就不需要执行这个规则。
 * An enum marks resource invocation direction.
 *
 * @author jialiang.linjl
 */
public enum EntryType {
    /**
     * Inbound traffic
     */
    IN("IN"),
    /**
     * Outbound traffic
     */
    OUT("OUT");

    private final String name;

    EntryType(String s) {
        name = s;
    }

    public boolean equalsName(String otherName) {
        return name.equals(otherName);
    }

    @Override
    public String toString() {
        return name;
    }
}
