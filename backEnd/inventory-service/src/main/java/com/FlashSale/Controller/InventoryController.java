package com.FlashSale.Controller;

import com.FlashSale.Common.InventoryReserveRequest;
import com.FlashSale.Common.InventoryReserveResponse;
import com.FlashSale.Common.InventoryRestoreRequest;
import com.FlashSale.Service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<InventoryReserveResponse> reserve(@RequestBody InventoryReserveRequest request) {
        InventoryReserveResponse response = inventoryService.reserve(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/restore")
    public ResponseEntity<?> restore(@RequestBody InventoryRestoreRequest request) {
        boolean restored = inventoryService.restore(request);
        if (restored) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body(java.util.Map.of("message", "参数非法"));
    }
}
