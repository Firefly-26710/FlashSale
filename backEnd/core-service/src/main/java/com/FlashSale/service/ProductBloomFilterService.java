package com.FlashSale.Service;

import com.FlashSale.Repository.ProductRepository;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProductBloomFilterService {

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;
    private RBloomFilter<Integer> bloomFilter;

    @Value("${flashsale.cache.product-bloom.key:product:bloom}")
    private String bloomKey;

    @Value("${flashsale.cache.product-bloom.expected-insertions:100000}")
    private long expectedInsertions;

    @Value("${flashsale.cache.product-bloom.false-probability:0.01}")
    private double falseProbability;

    @Value("${flashsale.cache.product-bloom.rebuild-lock-lease-seconds:120}")
    private long rebuildLockLeaseSeconds;

    public ProductBloomFilterService(RedissonClient redissonClient, ProductRepository productRepository) {
        this.redissonClient = redissonClient;
        this.productRepository = productRepository;
    }

    @PostConstruct
    public void init() {
        // 初始化 Bloom 过滤器实例（不存在时会自动创建）
        bloomFilter = redissonClient.getBloomFilter(bloomKey);
        // 初始化容量与误判率，若已初始化则保持原参数
        bloomFilter.tryInit(expectedInsertions, falseProbability);

        // 应用启动后尝试全量重建 Bloom，利用分布式锁保证仅单实例执行
        rebuild();
    }

    public void rebuild() {
        String rebuildLockKey = bloomKey + ":rebuild:lock";
        RLock rebuildLock = redissonClient.getLock(rebuildLockKey);
        boolean locked;
        try {
            locked = rebuildLock.tryLock(0, rebuildLockLeaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!locked) {
            // 未抢到锁说明已有实例在重建，当前实例直接跳过
            return;
        }

        try {
            // 全量重建：先删除旧过滤器，再按数据库商品 ID 重建
            bloomFilter.delete();
            bloomFilter.tryInit(expectedInsertions, falseProbability);

            List<Integer> productIds = productRepository.findAllIds();
            for (Integer productId : productIds) {
                put(productId);
            }
        } finally {
            if (locked && rebuildLock.isHeldByCurrentThread()) {
                rebuildLock.unlock();
            }
        }
    }

    public void put(int productId) {
        // 商品新增或命中数据库后回填到 Bloom，减少后续误拦截
        bloomFilter.add(productId);
    }

    public boolean mightContain(int productId) {
        // 返回 true 仅代表“可能存在”（Bloom 允许少量误判）
        return bloomFilter.contains(productId);
    }
}
