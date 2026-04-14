package com.FlashSale.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/config-demo")
@RefreshScope
public class ConfigDemoController {

    @Value("${flashsale.dynamic.message:hello-from-default}")
    private String dynamicMessage;

    @GetMapping("/message")
    public Map<String, Object> getDynamicMessage() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "core-service");
        payload.put("message", dynamicMessage);
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }
}
