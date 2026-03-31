package com.FlashSale.Service;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

// 支付请求消息体：由订单服务发送给支付服务。
public class PaymentRequestMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long orderId;
    private Integer userId;
    private BigDecimal amount;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
