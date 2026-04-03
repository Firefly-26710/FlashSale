package com.FlashSale.Service;

import com.FlashSale.Common.InventoryReserveResponse;
import com.FlashSale.Common.InventoryRestoreRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class InventoryReserveResultConsumer {

    private static final String ORDER_STATUS_KEY_PREFIX = "order:status:";
    private static final String ORDER_PENDING_KEY_PREFIX = "order:pending:user:";
    private static final String ORDER_STATUS_FIELD = "status";
    private static final String ORDER_MESSAGE_FIELD = "message";
    private static final Duration ORDER_STATUS_TTL = Duration.ofMinutes(15);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${flashsale.seckill.kafka.topic:flashsale.order.create}")
    private String seckillTopic;

    @Value("${flashsale.inventory.kafka.restore-topic:flashsale.inventory.restore}")
    private String inventoryRestoreTopic;

    public InventoryReserveResultConsumer(KafkaTemplate<String, Object> kafkaTemplate,
                                          StringRedisTemplate redisTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(
            topics = "${flashsale.inventory.kafka.result-topic:flashsale.inventory.reserve.result}",
            groupId = "${flashsale.inventory.kafka.result-group:flashsale-inventory-reserve-result-consumer}"
    )
    public void handleReserveResult(InventoryReserveResponse response) {
        if (response == null || response.getOrderId() == null) {
            return;
        }

        if (!response.isSuccess()) {
            markOrderRejected(response.getOrderId(), response.getUserId(), response.getMessage());
            clearPendingOrder(response.getUserId(), response.getProductId());
            return;
        }

        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(response.getOrderId());
        message.setUserId(response.getUserId());
        message.setProductId(response.getProductId());
        message.setQuantity(response.getQuantity());
        message.setAmount(response.getPrice());

        kafkaTemplate.send(seckillTopic, String.valueOf(response.getOrderId()), message)
                .whenComplete((sendResult, throwable) -> {
                    if (throwable != null) {
                        markOrderRejected(response.getOrderId(), response.getUserId(), "订单消息发送失败");
                        clearPendingOrder(response.getUserId(), response.getProductId());
                        InventoryRestoreRequest restoreRequest = new InventoryRestoreRequest();
                        restoreRequest.setOrderId(response.getOrderId());
                        restoreRequest.setUserId(response.getUserId());
                        restoreRequest.setProductId(response.getProductId());
                        restoreRequest.setQuantity(response.getQuantity());
                        kafkaTemplate.send(inventoryRestoreTopic, String.valueOf(response.getOrderId()), restoreRequest);
                    }
                });
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

    private void clearPendingOrder(Integer userId, Integer productId) {
        if (userId == null || productId == null) {
            return;
        }
        redisTemplate.delete(ORDER_PENDING_KEY_PREFIX + userId + ":product:" + productId);
    }
}
