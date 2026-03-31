package com.FlashSale.Service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
// 秒杀库存预热：应用启动后把商品库存同步到Redis。
public class SeckillStockWarmupService {

    private final InventoryService inventoryService;

    public SeckillStockWarmupService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // 启动后预热 Redis 秒杀库存。
    @EventListener(ApplicationReadyEvent.class)
    public void warmupStock() {
        inventoryService.preloadAllStockToRedis();
    }
}
