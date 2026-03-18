package com.FlashSale.Service;

import com.FlashSale.Entity.Product;
import com.FlashSale.Repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductService {

    // 商品列表缓存 key
    private static final String PRODUCT_LIST_KEY = "product:list:all";
    // 商品列表缓存重建锁 key
    private static final String PRODUCT_LIST_REBUILD_LOCK_KEY = "product:list:lock";
    // 商品详情缓存 key 前缀
    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product:detail:";
    // 商品详情缓存重建锁 key 前缀
    private static final String PRODUCT_DETAIL_REBUILD_LOCK_KEY_PREFIX = "product:detail:lock:";
    // 空值占位，避免不存在商品反复穿透到数据库
    private static final String NULL_VALUE_MARKER = "__NULL__";

    private final ProductRepository productRepository;
    private final ProductBloomFilterService bloomFilterService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${flashsale.cache.product-detail.ttl-seconds:600}")
    private long productDetailTtlSeconds;

    @Value("${flashsale.cache.product-detail.null-ttl-seconds:60}")
    private long productDetailNullTtlSeconds;

    @Value("${flashsale.cache.product-detail.jitter-max-seconds:300}")
    private long productDetailJitterMaxSeconds;

    @Value("${flashsale.cache.product-detail.rebuild-lock-seconds:10}")
    private long productDetailRebuildLockSeconds;

    @Value("${flashsale.cache.product-detail.rebuild-thread-pool-size:4}")
    private int productDetailRebuildThreadPoolSize;

    @Value("${flashsale.cache.product-list.ttl-seconds:300}")
    private long productListTtlSeconds;

    @Value("${flashsale.cache.product-list.jitter-max-seconds:180}")
    private long productListJitterMaxSeconds;

    @Value("${flashsale.cache.product-list.rebuild-lock-seconds:8}")
    private long productListRebuildLockSeconds;

    private ExecutorService rebuildExecutor;

    public ProductService(ProductRepository productRepository,
                          ProductBloomFilterService bloomFilterService,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.bloomFilterService = bloomFilterService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 商品列表查询：逻辑过期 + 异步重建，防止列表缓存同刻失效导致雪崩。
     */
    public List<Product> listProducts() {
        String cachedValue = redisTemplate.opsForValue().get(PRODUCT_LIST_KEY);
        if (cachedValue != null) {
            try {
                ProductListCacheEnvelope envelope = objectMapper.readValue(cachedValue, ProductListCacheEnvelope.class);
                if (envelope.getData() == null) {
                    redisTemplate.delete(PRODUCT_LIST_KEY);
                    return loadProductListFromDatabaseAndWriteCache();
                }

                long nowEpochSeconds = Instant.now().getEpochSecond();
                if (envelope.getLogicalExpireAtEpochSeconds() > nowEpochSeconds) {
                    return envelope.getData();
                }

                triggerAsyncListRebuild();
                return envelope.getData();
            } catch (JsonProcessingException exception) {
                redisTemplate.delete(PRODUCT_LIST_KEY);
            }
        }

        return loadProductListFromDatabaseAndWriteCache();
    }

    @PostConstruct
    public void init() {
        // 初始化异步重建线程池，用于逻辑过期后的后台刷新
        rebuildExecutor = Executors.newFixedThreadPool(Math.max(1, productDetailRebuildThreadPoolSize));
    }

    @PreDestroy
    public void destroy() {
        // 优雅关闭线程池，避免应用停止时仍有后台任务运行
        if (rebuildExecutor != null) {
            rebuildExecutor.shutdown();
        }
    }

    public Optional<Product> getProductById(int id) {
        // 第一层校验：非法 ID 直接拦截
        if (id <= 0) {
            return Optional.empty();
        }

        // 第二层校验：Bloom 过滤器快速拦截明显不存在的 ID
        if (!bloomFilterService.mightContain(id)) {
            return Optional.empty();
        }

        String cacheKey = PRODUCT_DETAIL_KEY_PREFIX + id;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            if (NULL_VALUE_MARKER.equals(cachedValue)) {
                // 命中空值缓存，直接返回不存在
                return Optional.empty();
            }
            try {
                ProductCacheEnvelope envelope = objectMapper.readValue(cachedValue, ProductCacheEnvelope.class);
                if (envelope.getData() == null) {
                    // 缓存结构异常时删除脏数据，避免持续命中错误值
                    redisTemplate.delete(cacheKey);
                    return loadFromDatabaseAndWriteCache(id, cacheKey);
                }

                long nowEpochSeconds = Instant.now().getEpochSecond();
                if (envelope.getLogicalExpireAtEpochSeconds() > nowEpochSeconds) {
                    // 逻辑未过期：直接返回缓存数据
                    return Optional.of(envelope.getData());
                }

                // 逻辑已过期：先返回旧值，再触发异步重建，避免热点 key 击穿
                triggerAsyncRebuild(id, cacheKey);
                return Optional.of(envelope.getData());
            } catch (JsonProcessingException exception) {
                // 缓存反序列化失败，删除后走数据库兜底
                redisTemplate.delete(cacheKey);
            }
        }

        // 冷启动或缓存缺失：同步回源数据库并回填缓存
        return loadFromDatabaseAndWriteCache(id, cacheKey);
    }

    //从数据库加载并写回缓存。
    private Optional<Product> loadFromDatabaseAndWriteCache(int id, String cacheKey) {
        Optional<Product> dbResult = productRepository.findById(id);
        if (dbResult.isPresent()) {
            // 查询到真实数据后补写 Bloom，避免新商品短期误判
            bloomFilterService.put(id);

            long randomJitter = ThreadLocalRandom.current().nextLong(productDetailJitterMaxSeconds + 1);
            long physicalTtlSeconds = productDetailTtlSeconds + randomJitter;
            long logicalExpireAt = Instant.now().getEpochSecond() + productDetailTtlSeconds;

            ProductCacheEnvelope envelope = new ProductCacheEnvelope();
            envelope.setData(dbResult.get());
            envelope.setLogicalExpireAtEpochSeconds(logicalExpireAt);

            try {
                // 物理TTL用于清理僵尸键；逻辑过期用于读路径的击穿保护
                String jsonValue = objectMapper.writeValueAsString(envelope);
                redisTemplate.opsForValue().set(cacheKey, jsonValue, Duration.ofSeconds(physicalTtlSeconds));
            } catch (JsonProcessingException ignored) {
                redisTemplate.delete(cacheKey);
            }

            return dbResult;
        }

        // DB 不存在：写入短 TTL 空值缓存，防止缓存穿透
        redisTemplate.opsForValue().set(cacheKey, NULL_VALUE_MARKER, Duration.ofSeconds(productDetailNullTtlSeconds));
        return Optional.empty();
    }

    private void triggerAsyncRebuild(int id, String cacheKey) {
        String rebuildLockKey = PRODUCT_DETAIL_REBUILD_LOCK_KEY_PREFIX + id;
        // 使用分布式短锁控制并发重建：同一时刻仅允许一个线程重建该商品缓存
        Boolean lockSuccess = redisTemplate.opsForValue().setIfAbsent(
                rebuildLockKey,
                "1",
                Duration.ofSeconds(productDetailRebuildLockSeconds)
        );

        if (!Boolean.TRUE.equals(lockSuccess)) {
            return;
        }

        rebuildExecutor.submit(() -> {
            try {
                // 后台刷新缓存，不阻塞当前请求
                loadFromDatabaseAndWriteCache(id, cacheKey);
            } finally {
                // 无论重建成功与否都释放重建锁，防止死锁
                redisTemplate.delete(rebuildLockKey);
            }
        });
    }

    /**
     * 应用预热入口：启动后可主动加载列表与部分详情缓存，缓解冷启动流量冲击。
     */
    public void warmupCaches(int warmupDetailCount) {
        listProducts();
        List<Integer> ids = productRepository.findAllIds();
        int limit = Math.max(0, Math.min(warmupDetailCount, ids.size()));
        for (int index = 0; index < limit; index++) {
            getProductById(ids.get(index));
        }
    }

    //从数据库加载商品列表并写回缓存。
    private List<Product> loadProductListFromDatabaseAndWriteCache() {
        List<Product> products = productRepository.findAll();

        long randomJitter = ThreadLocalRandom.current().nextLong(productListJitterMaxSeconds + 1);
        long physicalTtlSeconds = productListTtlSeconds + randomJitter;
        long logicalExpireAt = Instant.now().getEpochSecond() + productListTtlSeconds;

        ProductListCacheEnvelope envelope = new ProductListCacheEnvelope();
        envelope.setData(products);
        envelope.setLogicalExpireAtEpochSeconds(logicalExpireAt);

        try {
            String jsonValue = objectMapper.writeValueAsString(envelope);
            redisTemplate.opsForValue().set(PRODUCT_LIST_KEY, jsonValue, Duration.ofSeconds(physicalTtlSeconds));
        } catch (JsonProcessingException ignored) {
            redisTemplate.delete(PRODUCT_LIST_KEY);
        }

        return products;
    }

    //触发商品列表异步重建。
    private void triggerAsyncListRebuild() {
        Boolean lockSuccess = redisTemplate.opsForValue().setIfAbsent(
                PRODUCT_LIST_REBUILD_LOCK_KEY,
                "1",
                Duration.ofSeconds(productListRebuildLockSeconds)
        );

        if (!Boolean.TRUE.equals(lockSuccess)) {
            return;
        }

        rebuildExecutor.submit(() -> {
            try {
                loadProductListFromDatabaseAndWriteCache();
            } finally {
                redisTemplate.delete(PRODUCT_LIST_REBUILD_LOCK_KEY);
            }
        });
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProductCacheEnvelope {
        private Product data;
        // 逻辑过期时间（秒级时间戳）
        private long logicalExpireAtEpochSeconds;

        public Product getData() {
            return data;
        }

        public void setData(Product data) {
            this.data = data;
        }

        public long getLogicalExpireAtEpochSeconds() {
            return logicalExpireAtEpochSeconds;
        }

        public void setLogicalExpireAtEpochSeconds(long logicalExpireAtEpochSeconds) {
            this.logicalExpireAtEpochSeconds = logicalExpireAtEpochSeconds;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProductListCacheEnvelope {
        private List<Product> data;
        // 逻辑过期时间（秒级时间戳）
        private long logicalExpireAtEpochSeconds;

        public List<Product> getData() {
            return data;
        }

        public void setData(List<Product> data) {
            this.data = data;
        }

        public long getLogicalExpireAtEpochSeconds() {
            return logicalExpireAtEpochSeconds;
        }

        public void setLogicalExpireAtEpochSeconds(long logicalExpireAtEpochSeconds) {
            this.logicalExpireAtEpochSeconds = logicalExpireAtEpochSeconds;
        }
    }
}
