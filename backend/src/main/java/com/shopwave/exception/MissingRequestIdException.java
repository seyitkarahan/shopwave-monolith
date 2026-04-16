package com.shopwave.exception;

public class MissingRequestIdException extends RuntimeException {
    public MissingRequestIdException(String message) {
        super(message);
    }
}
