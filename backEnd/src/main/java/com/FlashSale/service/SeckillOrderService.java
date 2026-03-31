package com.FlashSale.Service;

import com.FlashSale.Entity.Order;
import com.FlashSale.Entity.Product;
import com.FlashSale.Repository.OrderRepository;
import com.FlashSale.Repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
// 秒杀订单服务：负责Redis原子扣减、Kafka投递、订单查询。
public class SeckillOrderService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_SET_KEY_PREFIX = "seckill:users:";
    private static final String STOCK_INIT_LOCK_PREFIX = "seckill:stock:init:lock:";
    private static final String PAYMENT_REQUEST_KEY_PREFIX = "order:payment:request:";

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
    private final OrderRepository orderRepository;
    private final SnowflakeIdService snowflakeIdService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${flashsale.seckill.kafka.topic:flashsale.order.create}")
    private String seckillTopic;

    @Value("${flashsale.payment.kafka.request-topic:flashsale.order.payment.request}")
    private String paymentRequestTopic;

    public SeckillOrderService(StringRedisTemplate redisTemplate,
                               ProductRepository productRepository,
                               OrderRepository orderRepository,
                               SnowflakeIdService snowflakeIdService,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.snowflakeIdService = snowflakeIdService;
        this.kafkaTemplate = kafkaTemplate;
    }

    // 秒杀入口：先在 Redis 做原子扣减 + 去重，再异步投递 Kafka 创建订单。
    public Map<String, Object> createSeckillOrder(Integer userId, Integer productId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            return Map.of("success", false, "message", "参数非法");
        }

        ensureStockLoaded(productId);

        String stockKey = STOCK_KEY_PREFIX + productId;
        String userSetKey = USER_SET_KEY_PREFIX + productId;

        Long result = redisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(stockKey, userSetKey),
                String.valueOf(userId),
                "1"
        );

        if (DUPLICATE_CODE.equals(result)) {
            return Map.of("success", false, "message", "请勿重复下单");
        }
        if (SOLD_OUT_CODE.equals(result)) {
            return Map.of("success", false, "message", "库存不足");
        }
        if (STOCK_NOT_INIT_CODE.equals(result)) {
            return Map.of("success", false, "message", "库存初始化失败，请稍后重试");
        }
        if (!SUCCESS_CODE.equals(result)) {
            return Map.of("success", false, "message", "系统繁忙，请稍后重试");
        }

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            rollbackRedisDeduction(productId, userId);
            return Map.of("success", false, "message", "商品不存在");
        }

        long orderId = snowflakeIdService.nextId();

        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(orderId);
        message.setUserId(userId);
        message.setProductId(productId);
        message.setQuantity(1);
        message.setAmount(productOpt.get().getPrice());

        kafkaTemplate.send(seckillTopic, String.valueOf(orderId), message)
                .whenComplete((sendResult, throwable) -> {
                    if (throwable != null) {
                        // Kafka投递失败时回滚Redis预扣，避免库存丢失。
                        rollbackRedisDeduction(productId, userId);
                    }
                });

        return Map.of(
                "success", true,
                "message", "秒杀请求已受理",
            "orderId", String.valueOf(orderId),
                "status", "PENDING"
        );
    }

    public Optional<Order> queryByOrderId(Long orderId) {
        return orderRepository.findById(orderId);
    }

    // 按用户查询订单列表。
    public List<Order> queryByUserId(Integer userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // 发起模拟支付：通过消息请求支付服务，回调再异步更新订单状态。
    public Map<String, Object> requestPayment(Long orderId, Integer userId) {
        if (orderId == null || userId == null) {
            return Map.of("success", false, "message", "参数非法");
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Map.of("success", false, "message", "订单不存在");
        }

        Order order = orderOpt.get();
        if (!userId.equals(order.getUserId())) {
            return Map.of("success", false, "message", "无权限操作该订单");
        }

        if ("PAID".equals(order.getStatus())) {
            return Map.of("success", true, "message", "订单已支付", "orderId", String.valueOf(order.getId()));
        }

        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return Map.of("success", false, "message", "当前订单状态不允许支付");
        }

        String requestLockKey = PAYMENT_REQUEST_KEY_PREFIX + orderId;
        Boolean freshRequest = redisTemplate.opsForValue().setIfAbsent(requestLockKey, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(freshRequest)) {
            return Map.of("success", false, "message", "支付请求处理中，请稍后查询订单状态");
        }

        PaymentRequestMessage message = new PaymentRequestMessage();
        message.setOrderId(order.getId());
        message.setUserId(order.getUserId());
        message.setAmount(order.getAmount());
        message.setRequestId(UUID.randomUUID().toString());

        kafkaTemplate.send(paymentRequestTopic, String.valueOf(order.getId()), message)
                .whenComplete((sendResult, throwable) -> {
                    if (throwable != null) {
                        redisTemplate.delete(requestLockKey);
                    }
                });

        return Map.of(
                "success", true,
                "message", "支付请求已受理",
                "orderId", String.valueOf(order.getId()),
                "status", "PENDING_PAYMENT"
        );
    }

    // 取消待支付订单：回补DB与Redis库存，同时释放用户限购标记。
    @Transactional
    public Map<String, Object> cancelPendingOrder(Long orderId, Integer userId) {
        if (orderId == null || userId == null) {
            return Map.of("success", false, "message", "参数非法");
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Map.of("success", false, "message", "订单不存在");
        }

        Order order = orderOpt.get();
        if (!userId.equals(order.getUserId())) {
            return Map.of("success", false, "message", "无权限操作该订单");
        }

        if ("CANCELLED".equals(order.getStatus())) {
            return Map.of("success", true, "message", "订单已取消", "orderId", String.valueOf(order.getId()), "status", "CANCELLED");
        }

        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return Map.of("success", false, "message", "当前订单状态不允许取消");
        }

        int changed = orderRepository.updateStatusIfCurrent(order.getId(), "PENDING_PAYMENT", "CANCELLED");
        if (changed <= 0) {
            return Map.of("success", false, "message", "订单状态已变化，请刷新后重试");
        }

        int quantity = order.getQuantity() == null || order.getQuantity() <= 0 ? 1 : order.getQuantity();
        productRepository.increaseStock(order.getProductId(), quantity);

        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + order.getProductId(), quantity);
        redisTemplate.opsForSet().remove(USER_SET_KEY_PREFIX + order.getProductId(), String.valueOf(order.getUserId()));
        redisTemplate.delete(PAYMENT_REQUEST_KEY_PREFIX + order.getId());

        return Map.of("success", true, "message", "订单已取消", "orderId", String.valueOf(order.getId()), "status", "CANCELLED");
    }

    // 预加载 Redis 库存，避免首次请求打到数据库。
    public void preloadAllStockToRedis() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            redisTemplate.opsForValue().set(
                    STOCK_KEY_PREFIX + product.getId(),
                    String.valueOf(Math.max(product.getStock(), 0))
            );
        }
    }

    // 单商品库存懒加载：仅当Redis库存不存在时回源数据库。
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

    // 极端场景兜底补偿：回滚 Redis 预扣库存与用户标记。
    private void rollbackRedisDeduction(Integer productId, Integer userId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String userSetKey = USER_SET_KEY_PREFIX + productId;
        redisTemplate.opsForValue().increment(stockKey);
        redisTemplate.opsForSet().remove(userSetKey, String.valueOf(userId));
    }
}
