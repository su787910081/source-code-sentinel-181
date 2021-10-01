package com.suyh1001.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/suyh")
@Slf4j
public class SuyhController {
    @RequestMapping("/name/{name}")
    public String name(@PathVariable("name") String name) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(100);
        return name;
    }
    
    @RequestMapping("/age/{age}")
    public Integer age(@PathVariable("age") Integer age) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(10);
        log.info("age: {}", age);
        return age;
    }
}
