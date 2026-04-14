package com.FlashSale.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayFallbackController {

    @GetMapping("/fallback/order")
    public ResponseEntity<Map<String, Object>> orderFallback() {
        return fallback("order-service", "订单服务暂时不可用，请稍后重试");
    }

    @GetMapping("/fallback/core")
    public ResponseEntity<Map<String, Object>> coreFallback() {
        return fallback("core-service", "商品服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<Map<String, Object>> fallback(String service, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", service);
        payload.put("message", message);
        payload.put("timestamp", Instant.now().toString());
        payload.put("degraded", true);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(payload);
    }
}
