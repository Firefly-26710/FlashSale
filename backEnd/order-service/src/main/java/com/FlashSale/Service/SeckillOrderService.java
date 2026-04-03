package com.FlashSale.Service;

import com.FlashSale.Common.InventoryReserveRequest;
import com.FlashSale.Common.InventoryRestoreRequest;
import com.FlashSale.Entity.Order;
import com.FlashSale.Repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
// 秒杀订单服务：负责库存预占、Kafka投递、订单查询。
public class SeckillOrderService {

    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("PENDING_PAYMENT", "PAID", "PAY_FAILED");
    private static final String PAYMENT_REQUEST_KEY_PREFIX = "order:payment:request:";
    private static final String ORDER_STATUS_KEY_PREFIX = "order:status:";
    private static final String ORDER_PENDING_KEY_PREFIX = "order:pending:user:";
    private static final String ORDER_STATUS_FIELD = "status";
    private static final String ORDER_USER_FIELD = "userId";
    private static final String ORDER_MESSAGE_FIELD = "message";
    private static final Duration ORDER_STATUS_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;
    private final SnowflakeIdService snowflakeIdService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderOutboxService outboxService;

    @Value("${flashsale.payment.kafka.request-topic:flashsale.order.payment.request}")
    private String paymentRequestTopic;

    @Value("${flashsale.inventory.kafka.reserve-topic:flashsale.inventory.reserve}")
    private String inventoryReserveTopic;

    @Value("${flashsale.inventory.kafka.restore-topic:flashsale.inventory.restore}")
    private String inventoryRestoreTopic;

    public SeckillOrderService(StringRedisTemplate redisTemplate,
                               OrderRepository orderRepository,
                               SnowflakeIdService snowflakeIdService,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               OrderOutboxService outboxService) {
        this.redisTemplate = redisTemplate;
        this.orderRepository = orderRepository;
        this.snowflakeIdService = snowflakeIdService;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxService = outboxService;
    }

    // 秒杀入口：先请求库存服务预占，再异步投递 Kafka 创建订单。
    public Map<String, Object> createSeckillOrder(Integer userId, Integer productId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            return Map.of("success", false, "message", "参数非法");
        }

        if (orderRepository.existsByUserIdAndProductIdAndStatusIn(userId, productId, ACTIVE_ORDER_STATUSES)) {
            return Map.of("success", false, "message", "已存在有效订单，无法重复秒杀");
        }

        String pendingKey = buildPendingKey(userId, productId);
        Boolean pendingLocked = redisTemplate.opsForValue().setIfAbsent(pendingKey, "1", ORDER_STATUS_TTL);
        if (!Boolean.TRUE.equals(pendingLocked)) {
            return Map.of("success", false, "message", "已有订单处理中，请稍后再试");
        }

        long orderId = snowflakeIdService.nextId();
        storeOrderStatus(orderId, userId, "PENDING", "秒杀请求已受理");

        InventoryReserveRequest reserveRequest = new InventoryReserveRequest();
        reserveRequest.setOrderId(orderId);
        reserveRequest.setUserId(userId);
        reserveRequest.setProductId(productId);
        reserveRequest.setQuantity(1);

        try {
            outboxService.enqueue(inventoryReserveTopic, String.valueOf(orderId), reserveRequest);
        } catch (Exception exception) {
            storeOrderStatus(orderId, userId, "FAILED", "库存请求失败，请稍后重试");
            redisTemplate.delete(pendingKey);
            return Map.of("success", false, "message", "库存请求失败，请稍后重试");
        }

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

    public Optional<Map<String, Object>> queryOrderStatus(Long orderId, Integer userId) {
        if (orderId == null || userId == null) {
            return Optional.empty();
        }

        String key = ORDER_STATUS_KEY_PREFIX + orderId;
        Object storedUserId = redisTemplate.opsForHash().get(key, ORDER_USER_FIELD);
        if (storedUserId == null) {
            return Optional.empty();
        }

        if (!String.valueOf(userId).equals(String.valueOf(storedUserId))) {
            return Optional.of(Map.of("status", "FORBIDDEN", "message", "无权限查看该订单"));
        }

        Object status = redisTemplate.opsForHash().get(key, ORDER_STATUS_FIELD);
        Object message = redisTemplate.opsForHash().get(key, ORDER_MESSAGE_FIELD);
        String statusValue = status == null ? "PENDING" : String.valueOf(status);
        if ("REJECTED".equals(statusValue) || "FAILED".equals(statusValue)) {
            statusValue = "CANCELLED";
        }
        return Optional.of(Map.of(
                "orderId", String.valueOf(orderId),
            "status", statusValue,
                "message", message == null ? "处理中" : String.valueOf(message)
        ));
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
            Optional<Map<String, Object>> statusOpt = queryOrderStatus(orderId, userId);
            if (statusOpt.isPresent()) {
                Object status = statusOpt.get().get("status");
                if ("PENDING".equals(status)) {
                    return Map.of("success", false, "message", "订单处理中，请稍后再试");
                }
                if ("CANCELLED".equals(status)) {
                    return Map.of("success", false, "message", "订单已取消");
                }
            }
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

        try {
            outboxService.enqueue(paymentRequestTopic, String.valueOf(order.getId()), message);
        } catch (Exception exception) {
            redisTemplate.delete(requestLockKey);
            return Map.of("success", false, "message", "支付请求失败，请稍后重试");
        }

        return Map.of(
                "success", true,
                "message", "支付请求已受理",
                "orderId", String.valueOf(order.getId()),
                "status", "PENDING_PAYMENT"
        );
    }

    // 取消待支付订单：回补库存并释放用户限购标记。
    @Transactional
    public Map<String, Object> cancelPendingOrder(Long orderId, Integer userId) {
        if (orderId == null || userId == null) {
            return Map.of("success", false, "message", "参数非法");
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            Optional<Map<String, Object>> statusOpt = queryOrderStatus(orderId, userId);
            if (statusOpt.isPresent()) {
                Object status = statusOpt.get().get("status");
                if ("PENDING".equals(status)) {
                    return Map.of("success", false, "message", "订单处理中，请稍后再试");
                }
                if ("CANCELLED".equals(status)) {
                    return Map.of("success", false, "message", "订单已取消");
                }
            }
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
        redisTemplate.delete(PAYMENT_REQUEST_KEY_PREFIX + order.getId());

        InventoryRestoreRequest restoreRequest = new InventoryRestoreRequest();
        restoreRequest.setOrderId(order.getId());
        restoreRequest.setUserId(order.getUserId());
        restoreRequest.setProductId(order.getProductId());
        restoreRequest.setQuantity(quantity);
        kafkaTemplate.send(inventoryRestoreTopic, String.valueOf(order.getId()), restoreRequest);

        return Map.of("success", true, "message", "订单已取消", "orderId", String.valueOf(order.getId()), "status", "CANCELLED");
    }

    private void storeOrderStatus(Long orderId, Integer userId, String status, String message) {
        String key = ORDER_STATUS_KEY_PREFIX + orderId;
        redisTemplate.opsForHash().put(key, ORDER_USER_FIELD, String.valueOf(userId));
        redisTemplate.opsForHash().put(key, ORDER_STATUS_FIELD, status);
        if (message != null) {
            redisTemplate.opsForHash().put(key, ORDER_MESSAGE_FIELD, message);
        }
        redisTemplate.expire(key, ORDER_STATUS_TTL);
    }

    private String buildPendingKey(Integer userId, Integer productId) {
        return ORDER_PENDING_KEY_PREFIX + userId + ":product:" + productId;
    }
}
