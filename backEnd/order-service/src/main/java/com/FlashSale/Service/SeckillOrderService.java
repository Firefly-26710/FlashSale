package com.FlashSale.Service;

import com.FlashSale.Common.InventoryReserveRequest;
import com.FlashSale.Common.InventoryReserveResponse;
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
import java.util.UUID;

@Service
// 秒杀订单服务：负责库存预占、Kafka投递、订单查询。
public class SeckillOrderService {

    private static final String PAYMENT_REQUEST_KEY_PREFIX = "order:payment:request:";

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;
    private final SnowflakeIdService snowflakeIdService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InventoryClient inventoryClient;

    @Value("${flashsale.seckill.kafka.topic:flashsale.order.create}")
    private String seckillTopic;

    @Value("${flashsale.payment.kafka.request-topic:flashsale.order.payment.request}")
    private String paymentRequestTopic;

    public SeckillOrderService(StringRedisTemplate redisTemplate,
                               OrderRepository orderRepository,
                               SnowflakeIdService snowflakeIdService,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               InventoryClient inventoryClient) {
        this.redisTemplate = redisTemplate;
        this.orderRepository = orderRepository;
        this.snowflakeIdService = snowflakeIdService;
        this.kafkaTemplate = kafkaTemplate;
        this.inventoryClient = inventoryClient;
    }

    // 秒杀入口：先请求库存服务预占，再异步投递 Kafka 创建订单。
    public Map<String, Object> createSeckillOrder(Integer userId, Integer productId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            return Map.of("success", false, "message", "参数非法");
        }

        InventoryReserveRequest reserveRequest = new InventoryReserveRequest();
        reserveRequest.setUserId(userId);
        reserveRequest.setProductId(productId);
        reserveRequest.setQuantity(1);

        InventoryReserveResponse reserveResponse = inventoryClient.reserve(reserveRequest);
        if (reserveResponse == null || !reserveResponse.isSuccess()) {
            String message = reserveResponse == null ? "库存服务不可用" : reserveResponse.getMessage();
            return Map.of("success", false, "message", message == null ? "库存预占失败" : message);
        }

        long orderId = snowflakeIdService.nextId();

        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(orderId);
        message.setUserId(userId);
        message.setProductId(productId);
        message.setQuantity(1);
        message.setAmount(reserveResponse.getPrice());

        kafkaTemplate.send(seckillTopic, String.valueOf(orderId), message)
                .whenComplete((sendResult, throwable) -> {
                    if (throwable != null) {
                        InventoryRestoreRequest restoreRequest = new InventoryRestoreRequest();
                        restoreRequest.setUserId(userId);
                        restoreRequest.setProductId(productId);
                        restoreRequest.setQuantity(1);
                        inventoryClient.restore(restoreRequest);
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

    // 取消待支付订单：回补库存并释放用户限购标记。
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
        redisTemplate.delete(PAYMENT_REQUEST_KEY_PREFIX + order.getId());

        InventoryRestoreRequest restoreRequest = new InventoryRestoreRequest();
        restoreRequest.setUserId(order.getUserId());
        restoreRequest.setProductId(order.getProductId());
        restoreRequest.setQuantity(quantity);
        inventoryClient.restore(restoreRequest);

        return Map.of("success", true, "message", "订单已取消", "orderId", String.valueOf(order.getId()), "status", "CANCELLED");
    }
}
