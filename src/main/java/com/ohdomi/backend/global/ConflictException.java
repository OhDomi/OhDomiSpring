package com.ohdomi.backend.global;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
