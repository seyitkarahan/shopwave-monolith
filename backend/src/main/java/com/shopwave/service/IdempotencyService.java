package com.shopwave.service;

import com.shopwave.domain.Order;
import com.shopwave.exception.MissingRequestIdException;
import com.shopwave.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final OrderRepository orderRepository;

    public String requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new MissingRequestIdException("X-Request-Id header is required and cannot be blank");
        }
        return requestId.trim();
    }

    @Transactional(readOnly = true)
    public Optional<Order> findExistingOrder(String requestId) {
        return orderRepository.findByIdempotencyKeyWithItems(requestId);
    }
}
