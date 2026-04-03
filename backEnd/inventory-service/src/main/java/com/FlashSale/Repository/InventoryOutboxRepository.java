package com.FlashSale.Repository;

import com.FlashSale.Entity.InventoryOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryOutboxRepository extends JpaRepository<InventoryOutbox, Long> {
    List<InventoryOutbox> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(String status, LocalDateTime now);
}
