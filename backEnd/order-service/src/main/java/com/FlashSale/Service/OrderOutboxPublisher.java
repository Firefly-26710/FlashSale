package com.FlashSale.Service;

import com.FlashSale.Entity.OrderOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderOutboxPublisher {

    private final OrderOutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderOutboxPublisher(OrderOutboxService outboxService,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // 后台定时发布 Outbox 消息，确保本地事务提交后可靠发送。
    @Scheduled(fixedDelayString = "${flashsale.outbox.publish-interval-ms:2000}")
    public void publishPendingMessages() {
        List<OrderOutbox> batch = outboxService.loadPendingBatch();
        for (OrderOutbox outbox : batch) {
            try {
                Object payload = objectMapper.readValue(outbox.getPayload(), Class.forName(outbox.getPayloadType()));
                kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), payload).get();
                outboxService.markSent(outbox);
            } catch (Exception exception) {
                outboxService.markFailed(outbox, exception.getMessage());
            }
        }
    }
}
