package com.FlashSale.Service;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

// 秒杀订单消息体：在Kafka中传递下单数据。
public class SeckillOrderMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private Integer userId;
    private Integer productId;
    private Integer quantity;
    private BigDecimal amount;

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

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
