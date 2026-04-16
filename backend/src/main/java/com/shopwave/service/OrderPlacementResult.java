package com.shopwave.service;

import com.shopwave.dto.OrderDto;

public record OrderPlacementResult(OrderDto order, boolean created) {
}
