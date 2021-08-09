package com.alibaba.csp.sentinel.dashboard.suyh.ext;

import com.alibaba.csp.sentinel.spi.Spi;
import com.alibaba.csp.sentinel.transport.heartbeat.SimpleHttpHeartbeatSender;

/**
 * 扩展：对心跳发送的扩展，com.alibaba.csp.sentinel.transport.HeartbeatSender 的实现的spi加载只取第一个
 * 而spi 加载会排序，使用注解Spi 的order 值。值越小优先级越高，默认值为0。
 */
@Spi(order = -100)
public class SimpleHttpHeartbeatSenderExt extends SimpleHttpHeartbeatSender {
    public SimpleHttpHeartbeatSenderExt() {
        System.out.println("SimpleHttpHeartbeatSenderExt----suyh");
    }

    @Override
    public boolean sendHeartbeat() throws Exception {
        // TODO: suyh - 在这里重写
        return super.sendHeartbeat();
    }
}
