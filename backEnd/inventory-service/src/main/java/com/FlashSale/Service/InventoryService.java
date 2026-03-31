package com.FlashSale.Service;

import com.FlashSale.Common.InventoryReserveRequest;
import com.FlashSale.Common.InventoryReserveResponse;
import com.FlashSale.Common.InventoryRestoreRequest;
import com.FlashSale.Entity.Product;
import com.FlashSale.Repository.ProductRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_SET_KEY_PREFIX = "seckill:users:";
    private static final String STOCK_INIT_LOCK_PREFIX = "seckill:stock:init:lock:";
    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product:detail:";
    private static final String PRODUCT_LIST_KEY = "product:list:all";

    private static final Long SUCCESS_CODE = 1L;
    private static final Long SOLD_OUT_CODE = 0L;
    private static final Long DUPLICATE_CODE = -2L;
    private static final Long STOCK_NOT_INIT_CODE = -3L;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setScriptText(
                "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then " +
                        "return -2 " +
                        "end " +
                        "local stock = redis.call('get', KEYS[1]) " +
                        "if (not stock) then return -3 end " +
                        "if (tonumber(stock) < tonumber(ARGV[2])) then return 0 end " +
                        "redis.call('decrby', KEYS[1], ARGV[2]) " +
                        "redis.call('sadd', KEYS[2], ARGV[1]) " +
                        "return 1"
        );
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;

    public InventoryService(StringRedisTemplate redisTemplate, ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    @Transactional
    public InventoryReserveResponse reserve(InventoryReserveRequest request) {
        InventoryReserveResponse response = new InventoryReserveResponse();
        if (request == null || request.getUserId() == null || request.getProductId() == null) {
            response.setSuccess(false);
            response.setMessage("参数非法");
            return response;
        }

        int quantity = request.getQuantity() == null || request.getQuantity() <= 0 ? 1 : request.getQuantity();
        Integer productId = request.getProductId();
        Integer userId = request.getUserId();

        ensureStockLoaded(productId);

        String stockKey = STOCK_KEY_PREFIX + productId;
        String userSetKey = USER_SET_KEY_PREFIX + productId;

        Long result = redisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(stockKey, userSetKey),
                String.valueOf(userId),
                String.valueOf(quantity)
        );

        if (DUPLICATE_CODE.equals(result)) {
            response.setSuccess(false);
            response.setMessage("请勿重复下单");
            return response;
        }
        if (SOLD_OUT_CODE.equals(result)) {
            response.setSuccess(false);
            response.setMessage("库存不足");
            return response;
        }
        if (STOCK_NOT_INIT_CODE.equals(result)) {
            response.setSuccess(false);
            response.setMessage("库存初始化失败，请稍后重试");
            return response;
        }
        if (!SUCCESS_CODE.equals(result)) {
            response.setSuccess(false);
            response.setMessage("系统繁忙，请稍后重试");
            return response;
        }

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            rollbackRedisDeduction(productId, userId, quantity);
            response.setSuccess(false);
            response.setMessage("商品不存在");
            return response;
        }

        int updatedRows = productRepository.deductStockIfAvailable(productId, quantity);
        if (updatedRows <= 0) {
            rollbackRedisDeduction(productId, userId, quantity);
            response.setSuccess(false);
            response.setMessage("库存不足");
            return response;
        }

        clearProductCache(productId);

        Product product = productOpt.get();
        response.setSuccess(true);
        response.setMessage("库存已预占");
        response.setProductId(productId);
        response.setUserId(userId);
        response.setQuantity(quantity);
        response.setPrice(product.getPrice());
        return response;
    }

    @Transactional
    public boolean restore(InventoryRestoreRequest request) {
        if (request == null || request.getUserId() == null || request.getProductId() == null) {
            return false;
        }

        int quantity = request.getQuantity() == null || request.getQuantity() <= 0 ? 1 : request.getQuantity();
        Integer productId = request.getProductId();
        Integer userId = request.getUserId();

        productRepository.increaseStock(productId, quantity);
        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + productId, quantity);
        redisTemplate.opsForSet().remove(USER_SET_KEY_PREFIX + productId, String.valueOf(userId));
        clearProductCache(productId);
        return true;
    }

    public void preloadAllStockToRedis() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            redisTemplate.opsForValue().set(
                    STOCK_KEY_PREFIX + product.getId(),
                    String.valueOf(Math.max(product.getStock(), 0))
            );
        }
    }

    private void ensureStockLoaded(Integer productId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String stock = redisTemplate.opsForValue().get(stockKey);
        if (stock != null) {
            return;
        }

        String lockKey = STOCK_INIT_LOCK_PREFIX + productId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(3));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            Product product = productRepository.findById(productId).orElse(null);
            int safeStock = product == null ? 0 : Math.max(product.getStock(), 0);
            redisTemplate.opsForValue().set(stockKey, String.valueOf(safeStock));
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void rollbackRedisDeduction(Integer productId, Integer userId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String userSetKey = USER_SET_KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(stockKey, quantity);
        redisTemplate.opsForSet().remove(userSetKey, String.valueOf(userId));
    }

    private void clearProductCache(Integer productId) {
        redisTemplate.delete(PRODUCT_DETAIL_KEY_PREFIX + productId);
        redisTemplate.delete(PRODUCT_LIST_KEY);
    }
}
