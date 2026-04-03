package com.FlashSale.Service;

import com.FlashSale.Entity.OrderOutbox;
import com.FlashSale.Repository.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderOutboxService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";

    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderOutboxService(OrderOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String topic, String messageKey, Object payload) {
        OrderOutbox outbox = new OrderOutbox();
        outbox.setTopic(topic);
        outbox.setMessageKey(messageKey);
        outbox.setPayload(toJson(payload));
        outbox.setPayloadType(payload.getClass().getName());
        outbox.setStatus(STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(LocalDateTime.now());
        outboxRepository.save(outbox);
    }

    public List<OrderOutbox> loadPendingBatch() {
        return outboxRepository.findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
                STATUS_PENDING,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void markSent(OrderOutbox outbox) {
        outbox.setStatus(STATUS_SENT);
        outbox.setSentAt(LocalDateTime.now());
        outbox.setLastError(null);
        outboxRepository.save(outbox);
    }

    @Transactional
    public void markFailed(OrderOutbox outbox, String errorMessage) {
        outbox.setRetryCount(outbox.getRetryCount() + 1);
        outbox.setLastError(truncate(errorMessage));
        outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(5));
        outboxRepository.save(outbox);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化消息 payload", exception);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
