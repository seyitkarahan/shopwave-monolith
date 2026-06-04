package com.shopwave.client;

import com.shopwave.exception.InsufficientStockException;
import com.shopwave.exception.InventoryServiceException;
import com.shopwave.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestInventoryClient implements InventoryClient {

    private final RestClient inventoryRestClient;

    @Override
    public InventoryView getByProductId(Long productId) {
        return execute(() -> inventoryRestClient.get()
                .uri("/api/v1/inventory/products/{productId}", productId)
                .retrieve()
                .body(InventoryView.class));
    }

    @Override
    public List<InventoryView> getLowStock(int threshold) {
        return execute(() -> inventoryRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/inventory/low-stock")
                        .queryParam("threshold", threshold)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}));
    }

    @Override
    public void initStock(Long productId, int quantity) {
        execute(() -> {
            inventoryRestClient.post()
                    .uri("/api/v1/inventory/products/init")
                    .body(new InitStockRequest(productId, quantity))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    @Override
    public void reserve(Long productId, int quantity) {
        postOperation("/api/v1/inventory/reserve", new StockOperationRequest(productId, quantity));
    }

    @Override
    public void release(Long productId, int quantity) {
        postOperation("/api/v1/inventory/release", new StockOperationRequest(productId, quantity));
    }

    @Override
    public void deduct(Long productId, int quantity) {
        postOperation("/api/v1/inventory/deduct", new StockOperationRequest(productId, quantity));
    }

    @Override
    public Map<String, String> addStock(Long productId, int quantity) {
        return execute(() -> inventoryRestClient.post()
                .uri("/api/v1/inventory/products/{productId}/add", productId)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}));
    }

    private void postOperation(String path, StockOperationRequest body) {
        execute(() -> {
            inventoryRestClient.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T execute(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException ex) {
            throw mapNetworkError(ex);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        }
    }

    private InventoryServiceException mapNetworkError(ResourceAccessException ex) {
        // LAB-8 TODO: timeout sonrası reserve yapıldı mı bilinmez — Saga / idempotent reserve gerekir
        log.error("Inventory service unreachable or timed out: {}", ex.getMessage());
        return new InventoryServiceException(
                "Inventory service unavailable (timeout or connection failure). "
                        + "Request may have been applied — state is ambiguous until LAB-8 Saga.",
                ex);
    }

    private RuntimeException mapResponseException(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String body = ex.getResponseBodyAsString();
        String detail = extractDetail(body);
        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException(detail != null ? detail : "Inventory not found");
        }
        if (status == HttpStatus.CONFLICT) {
            return new InsufficientStockException(detail != null ? detail : "Insufficient stock");
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return new InventoryServiceException("Inventory service temporarily unavailable: " + detail);
        }
        return new InventoryServiceException(
                "Inventory service error: HTTP " + ex.getStatusCode().value()
                        + (detail != null ? " — " + detail : ""));
    }

    private String extractDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        // ProblemDetail JSON — basit parse
        int idx = body.indexOf("\"detail\"");
        if (idx < 0) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
        int start = body.indexOf(':', idx) + 1;
        int q1 = body.indexOf('"', start + 1);
        int q2 = body.indexOf('"', q1 + 1);
        if (q1 >= 0 && q2 > q1) {
            return body.substring(q1 + 1, q2);
        }
        return body;
    }
}
