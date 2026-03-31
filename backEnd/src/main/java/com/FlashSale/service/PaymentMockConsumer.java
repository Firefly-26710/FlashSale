package com.FlashSale.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
// 模拟支付服务：消费支付请求并回发支付结果（不接入真实支付渠道）。
public class PaymentMockConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${flashsale.payment.kafka.result-topic:flashsale.order.payment.result}")
    private String paymentResultTopic;

    public PaymentMockConsumer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "${flashsale.payment.kafka.request-topic:flashsale.order.payment.request}",
            groupId = "${flashsale.payment.kafka.mock-group:flashsale-payment-mock-consumer}"
    )
    public void consumePaymentRequest(PaymentRequestMessage request) {
        if (request == null || request.getOrderId() == null) {
            return;
        }

        PaymentResultMessage result = new PaymentResultMessage();
        result.setRequestId(request.getRequestId());
        result.setOrderId(request.getOrderId());
        // 课程作业阶段先模拟成功，可按需扩展失败分支和重试策略。
        result.setSuccess(true);
        result.setReason("MOCK_SUCCESS");

        kafkaTemplate.send(paymentResultTopic, String.valueOf(request.getOrderId()), result);
    }
}
