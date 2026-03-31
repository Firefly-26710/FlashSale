package com.FlashSale.Entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_product_id", columnList = "product_id")
        }
)
@Data
public class Order implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(nullable = false)
        @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 32)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
