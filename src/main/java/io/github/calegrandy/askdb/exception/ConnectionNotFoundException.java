package io.github.calegrandy.askdb.exception;

public class ConnectionNotFoundException extends RuntimeException {

    public ConnectionNotFoundException(String message) {
        super(message);
    }
}
