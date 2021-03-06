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
package com.suyh.csp.sentinel.demo.cluter.alone.client.app.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.stereotype.Service;

/**
 * @author Eric Zhao
 */
@Service
public class DemoService {

    // 特别注意下，这里的value值要修改为我们定义的resource，也就是我们在Nacos clusterDemo-flow-rules配置项对应的resource
    @SentinelResource(value = "cluster-resource", blockHandler = "sayHelloBlockHandler")
    public String sayHello(String name) {
        return "Hello, " + name;
    }

    public String sayHelloBlockHandler(String name, BlockException ex) {
        // This is the block handler.
        ex.printStackTrace();
        return String.format("Oops, <%s> blocked by Sentinel", name);
    }
}
