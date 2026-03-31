package com.FlashSale.Repository;

import com.FlashSale.Entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByUserIdAndProductIdAndStatusIn(Integer userId, Integer productId, Collection<String> statuses);

    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    @Modifying
    @Query("update Order o set o.status = :targetStatus where o.id = :orderId and o.status = :currentStatus")
    int updateStatusIfCurrent(@Param("orderId") Long orderId,
                              @Param("currentStatus") String currentStatus,
                              @Param("targetStatus") String targetStatus);
}
