package com.example.logicomposer.exception;

public class ConnectionNotFoundException extends RuntimeException {

    public ConnectionNotFoundException(String message) {
        super(message);
    }
}
