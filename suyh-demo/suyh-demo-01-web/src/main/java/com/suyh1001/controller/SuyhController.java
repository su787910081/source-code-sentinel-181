package com.suyh1001.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/suyh")
@Slf4j
public class SuyhController {
    @RequestMapping("/name")
    public String name() {
        log.info("name");
        return "suyh";
    }
    
    @RequestMapping("/age")
    public Integer age() {
        return 22;
    }
}
