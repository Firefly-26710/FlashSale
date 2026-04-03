package com.FlashSale.Service;

import com.FlashSale.Common.InventoryReserveRequest;
import com.FlashSale.Common.InventoryReserveResponse;
import com.FlashSale.Common.InventoryRestoreRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class InventoryKafkaConsumer {

    private final InventoryService inventoryService;
    private final InventoryOutboxService outboxService;

    @Value("${flashsale.inventory.kafka.result-topic:flashsale.inventory.reserve.result}")
    private String reserveResultTopic;

    public InventoryKafkaConsumer(InventoryService inventoryService,
                                  InventoryOutboxService outboxService) {
        this.inventoryService = inventoryService;
        this.outboxService = outboxService;
    }

    @KafkaListener(
            topics = "${flashsale.inventory.kafka.reserve-topic:flashsale.inventory.reserve}",
            groupId = "${flashsale.inventory.kafka.reserve-group:flashsale-inventory-reserve-consumer}"
    )
    public void handleReserve(InventoryReserveRequest request) {
        if (request == null) {
            return;
        }

        InventoryReserveResponse response = inventoryService.reserve(request);
        String key = response.getOrderId() == null ? String.valueOf(request.getProductId()) : String.valueOf(response.getOrderId());
        outboxService.enqueue(reserveResultTopic, key, response);
    }

    @KafkaListener(
            topics = "${flashsale.inventory.kafka.restore-topic:flashsale.inventory.restore}",
            groupId = "${flashsale.inventory.kafka.restore-group:flashsale-inventory-restore-consumer}"
    )
    public void handleRestore(InventoryRestoreRequest request) {
        if (request == null) {
            return;
        }

        inventoryService.restore(request);
    }
}
