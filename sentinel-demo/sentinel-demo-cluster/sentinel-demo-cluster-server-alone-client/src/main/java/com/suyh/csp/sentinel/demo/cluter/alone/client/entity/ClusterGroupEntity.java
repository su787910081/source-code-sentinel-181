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
package com.suyh.csp.sentinel.demo.cluter.alone.client.entity;

import java.util.Set;

/**
 * suyh - 当前实体在nacos 配置中的示例
 * format: [{"clientSet":["112.12.88.66@8729","112.12.88.67@8727"],"ip":"112.12.88.68","machineId":"112.12.88.68@8728","port":11111}]
 *
 * @author Eric Zhao
 * @since 1.4.1
 */
public class ClusterGroupEntity {

    // suyh - 服务器的机器 ID，默认是以"ip@port"的形式组成
    // suyh - 这里的port 对应的是配置项："csp.sentinel.api.port"
    private String machineId;
    private String ip;
    // suyh - 这里的端口是作为TokenServer 的监听端口
    private Integer port;

    // suyh - 这里的值与machineId 的值类似的，但是这里面配置的都是客户端(TokenClient)
    private Set<String> clientSet;

    public String getMachineId() {
        return machineId;
    }

    public ClusterGroupEntity setMachineId(String machineId) {
        this.machineId = machineId;
        return this;
    }

    public String getIp() {
        return ip;
    }

    public ClusterGroupEntity setIp(String ip) {
        this.ip = ip;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public ClusterGroupEntity setPort(Integer port) {
        this.port = port;
        return this;
    }

    public Set<String> getClientSet() {
        return clientSet;
    }

    public ClusterGroupEntity setClientSet(Set<String> clientSet) {
        this.clientSet = clientSet;
        return this;
    }

    @Override
    public String toString() {
        return "ClusterGroupEntity{" +
            "machineId='" + machineId + '\'' +
            ", ip='" + ip + '\'' +
            ", port=" + port +
            ", clientSet=" + clientSet +
            '}';
    }
}
