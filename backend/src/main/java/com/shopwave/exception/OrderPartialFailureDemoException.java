package com.shopwave.exception;

/** LAB-6: stok rezerve edildi, sipariş kaydedilmedi — Saga gerekir (LAB-8) */
public class OrderPartialFailureDemoException extends RuntimeException {
    public OrderPartialFailureDemoException() {
        super("LAB-6 demo: stock was reserved via HTTP but order was NOT saved. "
                + "Inconsistent state — compensation required (LAB-8 Saga).");
    }
}