package com.FlashSale.Service;

import java.io.Serial;
import java.io.Serializable;

// 支付结果消息体：由支付服务回传给订单服务。
public class PaymentResultMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String requestId;
    private Long orderId;
    private boolean success;
    private String reason;

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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
