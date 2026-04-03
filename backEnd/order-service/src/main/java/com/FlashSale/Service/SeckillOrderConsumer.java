package com.FlashSale.Service;

import com.FlashSale.Common.InventoryRestoreRequest;
import com.FlashSale.Entity.Order;
import com.FlashSale.Repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

@Service
// 秒杀订单消费者：消费Kafka消息并在数据库中落单。
public class SeckillOrderConsumer {

    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("PENDING_PAYMENT", "PAID", "PAY_FAILED");
    private static final String ORDER_STATUS_KEY_PREFIX = "order:status:";
    private static final String ORDER_PENDING_KEY_PREFIX = "order:pending:user:";
    private static final String ORDER_STATUS_FIELD = "status";
    private static final String ORDER_MESSAGE_FIELD = "message";
    private static final Duration ORDER_STATUS_TTL = Duration.ofMinutes(15);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${flashsale.inventory.kafka.restore-topic:flashsale.inventory.restore}")
    private String inventoryRestoreTopic;

    public SeckillOrderConsumer(OrderRepository orderRepository,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    // Kafka消费订单创建消息。
    // 通过事务保证订单写入的幂等性。
    @Transactional
    @KafkaListener(topics = "${flashsale.seckill.kafka.topic:flashsale.order.create}", groupId = "${flashsale.seckill.kafka.group:flashsale-order-consumer}")
    public void consumeOrderCreate(SeckillOrderMessage message) {
        if (message == null || message.getOrderId() == null) {
            return;
        }

        if (orderRepository.existsById(message.getOrderId())) {
            clearOrderStatus(message.getOrderId());
            clearPendingOrder(message.getUserId(), message.getProductId());
            return;
        }

        if (orderRepository.existsByUserIdAndProductIdAndStatusIn(message.getUserId(), message.getProductId(), ACTIVE_ORDER_STATUSES)) {
            markOrderRejected(message.getOrderId(), message.getUserId(), "已存在有效订单");
            sendRestore(message);
            clearPendingOrder(message.getUserId(), message.getProductId());
            return;
        }

        Order order = new Order();
        order.setId(message.getOrderId());
        order.setUserId(message.getUserId());
        order.setProductId(message.getProductId());
        order.setQuantity(message.getQuantity());
        order.setAmount(message.getAmount());
        order.setStatus("PENDING_PAYMENT");

        try {
            orderRepository.save(order);
            clearOrderStatus(message.getOrderId());
            clearPendingOrder(message.getUserId(), message.getProductId());
        } catch (Exception exception) {
            markOrderRejected(message.getOrderId(), message.getUserId(), "订单创建失败");
            sendRestore(message);
            clearPendingOrder(message.getUserId(), message.getProductId());
        }

    }

    private void sendRestore(SeckillOrderMessage message) {
        InventoryRestoreRequest restoreRequest = new InventoryRestoreRequest();
        restoreRequest.setOrderId(message.getOrderId());
        restoreRequest.setUserId(message.getUserId());
        restoreRequest.setProductId(message.getProductId());
        restoreRequest.setQuantity(message.getQuantity());
        kafkaTemplate.send(inventoryRestoreTopic, String.valueOf(message.getOrderId()), restoreRequest);
    }

    private void markOrderRejected(Long orderId, Integer userId, String reason) {
        String key = ORDER_STATUS_KEY_PREFIX + orderId;
        if (userId != null) {
            redisTemplate.opsForHash().put(key, "userId", String.valueOf(userId));
        }
        redisTemplate.opsForHash().put(key, ORDER_STATUS_FIELD, "REJECTED");
        if (reason != null) {
            redisTemplate.opsForHash().put(key, ORDER_MESSAGE_FIELD, reason);
        }
        redisTemplate.expire(key, ORDER_STATUS_TTL);
    }

    private void clearOrderStatus(Long orderId) {
        redisTemplate.delete(ORDER_STATUS_KEY_PREFIX + orderId);
    }

    private void clearPendingOrder(Integer userId, Integer productId) {
        if (userId == null || productId == null) {
            return;
        }
        redisTemplate.delete(ORDER_PENDING_KEY_PREFIX + userId + ":product:" + productId);
    }


}
