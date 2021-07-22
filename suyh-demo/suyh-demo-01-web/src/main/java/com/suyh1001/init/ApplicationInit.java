
package com.suyh1001.init;

import com.alibaba.csp.sentinel.init.InitExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ApplicationInit implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 在应用启动之后，sentinel 立即 生效。
        InitExecutor.doInit();
        
        String appName = SentinelConfig.getAppName();
        int appType = SentinelConfig.getAppType();
        List<Endpoint> consoleServerList = TransportConfig.getConsoleServerList();
        String transportIp = TransportConfig.getHeartbeatClientIp();
        String transportPort = TransportConfig.getPort();
        log.info("gateway sentinel init, app name: {}", appName);
        log.info("gateway sentinel init, app type: {}", appType);
        log.info("gateway sentinel init, console server list: {}", consoleServerList.toString());
        log.info("gateway sentinel init, transportIp: {}", transportIp);
        log.info("gateway sentinel init, transportPort: {}", transportPort);
    }
}
