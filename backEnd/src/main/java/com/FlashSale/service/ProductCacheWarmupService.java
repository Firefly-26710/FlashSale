package com.FlashSale.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProductCacheWarmupService {

    private final ProductService productService;

    @Value("${flashsale.cache.warmup.product-detail-count:20}")
    private int warmupProductDetailCount;

    public ProductCacheWarmupService(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 应用启动完成后执行缓存预热。
     * 预热内容：商品列表 + 前N个商品详情。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        productService.warmupCaches(warmupProductDetailCount);
    }
}
