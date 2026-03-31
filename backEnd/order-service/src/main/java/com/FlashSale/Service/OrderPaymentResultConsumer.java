package com.FlashSale.Service;

import com.FlashSale.Repository.OrderRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// 订单支付结果消费者：幂等更新订单状态，保障“支付结果 + 状态更新”最终一致。
public class OrderPaymentResultConsumer {

    private final OrderRepository orderRepository;

    public OrderPaymentResultConsumer(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    @KafkaListener(
            topics = "${flashsale.payment.kafka.result-topic:flashsale.order.payment.result}",
            groupId = "${flashsale.payment.kafka.order-group:flashsale-order-payment-consumer}"
    )
    public void consumePaymentResult(PaymentResultMessage result) {
        if (result == null || result.getOrderId() == null) {
            return;
        }

        String targetStatus = result.isSuccess() ? "PAID" : "PAY_FAILED";
        // 只允许从待支付变更，重复回调不会导致状态错乱。
        orderRepository.updateStatusIfCurrent(result.getOrderId(), "PENDING_PAYMENT", targetStatus);
    }
}
