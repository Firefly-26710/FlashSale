package com.FlashSale.Service;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class ApiRateLimitService {

    private static final String PRODUCT_LIST_LIMITER_KEY = "ratelimit:api:products:list";
    private static final String PRODUCT_DETAIL_LIMITER_KEY = "ratelimit:api:products:detail";

    private final RedissonClient redissonClient;

    @Value("${flashsale.ratelimit.product-list.rps:200}")
    private int productListRps;

    @Value("${flashsale.ratelimit.product-detail.rps:500}")
    private int productDetailRps;

    private RRateLimiter productListRateLimiter;
    private RRateLimiter productDetailRateLimiter;

    public ApiRateLimitService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void init() {
        productListRateLimiter = initLimiter(PRODUCT_LIST_LIMITER_KEY, productListRps);
        productDetailRateLimiter = initLimiter(PRODUCT_DETAIL_LIMITER_KEY, productDetailRps);
    }

    //商品列表接口限流。
    public boolean allowProductListRequest() {
        return productListRateLimiter.tryAcquire();
    }

    //商品详情接口限流。
    public boolean allowProductDetailRequest() {
        return productDetailRateLimiter.tryAcquire();
    }

    private RRateLimiter initLimiter(String key, int rps) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, Math.max(1, rps), 1, RateIntervalUnit.SECONDS);
        return rateLimiter;
    }
}
