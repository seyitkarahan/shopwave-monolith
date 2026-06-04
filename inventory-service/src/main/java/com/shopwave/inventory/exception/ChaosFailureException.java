package com.shopwave.inventory.exception;

/** LAB-6: yapay 503 — monolith timeout / partial failure gözlemi */
public class ChaosFailureException extends RuntimeException {
    public ChaosFailureException() {
        super("Chaos: simulated inventory service failure");
    }
}
