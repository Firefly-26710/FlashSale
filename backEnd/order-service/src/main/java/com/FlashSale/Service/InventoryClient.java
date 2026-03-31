package com.FlashSale.Service;

import com.FlashSale.Common.InventoryReserveRequest;
import com.FlashSale.Common.InventoryReserveResponse;
import com.FlashSale.Common.InventoryRestoreRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;

@Service
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventoryBaseUrl;

    public InventoryClient(RestTemplateBuilder builder,
                           @Value("${flashsale.inventory.base-url:http://inventory-service:6007}") String inventoryBaseUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .errorHandler(new DefaultResponseErrorHandler() {
                    @Override
                    public boolean hasError(ClientHttpResponse response) throws IOException {
                        return false;
                    }
                })
                .build();
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    public InventoryReserveResponse reserve(InventoryReserveRequest request) {
        try {
            ResponseEntity<InventoryReserveResponse> response = restTemplate.postForEntity(
                    inventoryBaseUrl + "/api/inventory/reserve",
                    request,
                    InventoryReserveResponse.class
            );
            InventoryReserveResponse body = response.getBody();
            if (body != null) {
                return body;
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                InventoryReserveResponse failure = new InventoryReserveResponse();
                failure.setSuccess(false);
                failure.setMessage("库存预占失败");
                return failure;
            }
        } catch (RestClientException ignored) {
        }

        InventoryReserveResponse fallback = new InventoryReserveResponse();
        fallback.setSuccess(false);
        fallback.setMessage("库存服务不可用");
        return fallback;
    }

    public boolean restore(InventoryRestoreRequest request) {
        try {
            restTemplate.postForEntity(
                    inventoryBaseUrl + "/api/inventory/restore",
                    request,
                    Void.class
            );
            return true;
        } catch (RestClientException exception) {
            return false;
        }
    }
}
