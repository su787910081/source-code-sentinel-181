

package com.suyh1001.config;

import com.alibaba.csp.sentinel.adapter.servlet.CommonFilter;
import com.alibaba.csp.sentinel.adapter.servlet.callback.DefaultUrlBlockHandler;
import com.alibaba.csp.sentinel.adapter.servlet.callback.WebCallbackManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;


@Configuration
@Slf4j
public class BeansConfiguration {
    @Bean
    public FilterRegistrationBean<Filter> sentinelFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        // suyh - 注册CommonFilter 过滤器，用于拦截所有的请求并添加Sentinel 的入口
        registration.setFilter(new CommonFilter());
        registration.addUrlPatterns("/*");
        registration.setName("sentinelFilter");
        registration.setOrder(1);

        log.info("Sentinel servlet CommonFilter registered");

        return registration;
    }

    @PostConstruct
    public void init() {
        WebCallbackManager.setUrlBlockHandler(new DefaultUrlBlockHandler());
    }
}
