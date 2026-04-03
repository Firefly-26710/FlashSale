package com.FlashSale.Repository;

import com.FlashSale.Entity.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {
    List<OrderOutbox> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(String status, LocalDateTime now);
}
