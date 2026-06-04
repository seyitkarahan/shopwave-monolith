package com.shopwave.service;

import com.shopwave.client.InventoryClient;
import com.shopwave.domain.*;
import com.shopwave.domain.Order.OrderStatus;
import com.shopwave.dto.OrderDto;
import com.shopwave.dto.OrderDto.OrderItemDto;
import com.shopwave.dto.PlaceOrderRequest;
import com.shopwave.exception.InvalidOrderStateException;
import com.shopwave.exception.NotFoundException;
import com.shopwave.exception.OrderPartialFailureDemoException;
import com.shopwave.repository.CustomerRepository;
import com.shopwave.repository.OrderRepository;
import com.shopwave.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LAB-6: placeOrder artık dağıtık akış —
 *   1) Tüm kalemler için stok HTTP ile rezerve edilir (inventory-service)
 *   2) Sipariş monolith DB'ye kaydedilir
 * Tek @Transactional sadece adım 2'yi kapsar; adım 1 ayrı process → partial failure mümkün.
 * LAB-8 TODO: Saga ile reserve başarısız / sipariş başarısız durumlarında compensation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository    orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository  productRepository;
    private final InventoryClient    inventoryClient;
    private final AuditService       auditService;

    @Value("${shopwave.lab6.partial-failure-demo.enabled:false}")
    private boolean partialFailureDemo;

    @Transactional(readOnly = true)
    public OrderDto getById(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public OrderDto getByRef(String ref) {
        Order order = orderRepository.findByOrderRef(ref)
                .orElseThrow(() -> new NotFoundException("Order not found: " + ref));
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::toDto).toList();
    }

    /**
     * Sipariş ver — LAB-6 dağıtık sıra: önce HTTP reserve, sonra order save.
     */
    @Transactional
    public OrderDto placeOrder(PlaceOrderRequest req) {
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new NotFoundException("Customer not found: " + req.getCustomerId()));

        Order order = Order.builder()
                .orderRef(generateOrderRef())
                .customer(customer)
                .status(OrderStatus.PENDING)
                .shippingAddress(req.getShippingAddress())
                .items(new ArrayList<>())
                .build();

        List<ReservedLine> reserved = new ArrayList<>();

        for (PlaceOrderRequest.OrderItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findByIdWithLock(itemReq.getProductId())
                    .orElseThrow(() -> new NotFoundException("Product not found: " + itemReq.getProductId()));

            if (!product.isActive()) {
                throw new IllegalArgumentException("Product is not active: " + product.getSku());
            }

            inventoryClient.reserve(product.getId(), itemReq.getQuantity());
            reserved.add(new ReservedLine(product.getId(), itemReq.getQuantity()));
            log.info("LAB-6: HTTP reserve OK productId={} qty={}", product.getId(), itemReq.getQuantity());

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            order.getItems().add(item);
        }

        if (partialFailureDemo) {
            log.error("LAB-6 PARTIAL FAILURE DEMO: reserved {} line(s) but order will NOT be saved. "
                    + "reserved={}", reserved.size(), reserved);
            throw new OrderPartialFailureDemoException();
        }

        order.recalculateTotal();
        orderRepository.save(order);

        auditService.log("ORDER_PLACED", "Order", order.getId(),
                "ref=" + order.getOrderRef() + " total=" + order.getTotalAmount()
                + " items=" + order.getItems().size());

        log.info("Order placed ref={} customerId={} total={}",
                order.getOrderRef(), customer.getId(), order.getTotalAmount());

        return toDto(order);
    }

    @Transactional
    public OrderDto confirm(Long id) {
        Order order = getOrderForUpdate(id);
        requireStatus(order, OrderStatus.PENDING);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        auditService.log("ORDER_CONFIRMED", "Order", id, "ref=" + order.getOrderRef());
        return toDto(order);
    }

    @Transactional
    public OrderDto ship(Long id) {
        Order order = getOrderForUpdate(id);
        requireStatus(order, OrderStatus.CONFIRMED);
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);
        auditService.log("ORDER_SHIPPED", "Order", id, null);
        return toDto(order);
    }

    @Transactional
    public OrderDto deliver(Long id) {
        Order order = getOrderForUpdate(id);
        requireStatus(order, OrderStatus.SHIPPED);
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        for (OrderItem item : order.getItems()) {
            inventoryClient.deduct(item.getProduct().getId(), item.getQuantity());
        }

        auditService.log("ORDER_DELIVERED", "Order", id, null);
        return toDto(order);
    }

    @Transactional
    public OrderDto cancel(Long id) {
        Order order = getOrderForUpdate(id);
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        for (OrderItem item : order.getItems()) {
            inventoryClient.release(item.getProduct().getId(), item.getQuantity());
        }

        auditService.log("ORDER_CANCELLED", "Order", id, null);
        log.info("Order cancelled ref={}", order.getOrderRef());
        return toDto(order);
    }

    private Order getOrderForUpdate(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    private void requireStatus(Order order, OrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new InvalidOrderStateException(
                "Expected status %s but was %s".formatted(expected, order.getStatus()));
        }
    }

    private String generateOrderRef() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    OrderDto toDto(Order o) {
        List<OrderItemDto> items = o.getItems() == null ? List.of() :
            o.getItems().stream().map(i -> OrderItemDto.builder()
                .productId(i.getProduct().getId())
                .sku(i.getProduct().getSku())
                .productName(i.getProduct().getName())
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .lineTotal(i.lineTotal())
                .build()).toList();

        return OrderDto.builder()
                .id(o.getId())
                .orderRef(o.getOrderRef())
                .customerId(o.getCustomer().getId())
                .customerName(o.getCustomer().getFullName())
                .status(o.getStatus())
                .totalAmount(o.getTotalAmount())
                .shippingAddress(o.getShippingAddress())
                .items(items)
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private record ReservedLine(Long productId, int quantity) {}
}
