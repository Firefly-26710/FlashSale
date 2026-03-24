package com.FlashSale.Repository;

import com.FlashSale.Entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByUserIdAndProductId(Integer userId, Integer productId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
