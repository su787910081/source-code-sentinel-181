package com.alibaba.csp.sentinel.demo.spring.sc.gateway;

import com.alibaba.csp.sentinel.init.InitExecutor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 期望在程序运行起来之后sentinel 立即生效，开始发送心跳。
 */
@Component
public class GatewayInit implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        InitExecutor.doInit();
    }
}
