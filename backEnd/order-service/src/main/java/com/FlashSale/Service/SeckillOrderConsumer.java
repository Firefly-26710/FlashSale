package com.FlashSale.Service;

import com.FlashSale.Entity.Order;
import com.FlashSale.Repository.OrderRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
// 秒杀订单消费者：消费Kafka消息并在数据库中落单。
public class SeckillOrderConsumer {

    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("PENDING_PAYMENT", "PAID", "PAY_FAILED");

    private final OrderRepository orderRepository;

    public SeckillOrderConsumer(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
            return;
        }

        if (orderRepository.existsByUserIdAndProductIdAndStatusIn(message.getUserId(), message.getProductId(), ACTIVE_ORDER_STATUSES)) {
            return;
        }

        Order order = new Order();
        order.setId(message.getOrderId());
        order.setUserId(message.getUserId());
        order.setProductId(message.getProductId());
        order.setQuantity(message.getQuantity());
        order.setAmount(message.getAmount());
        order.setStatus("PENDING_PAYMENT");

        orderRepository.save(order);

    }


}
