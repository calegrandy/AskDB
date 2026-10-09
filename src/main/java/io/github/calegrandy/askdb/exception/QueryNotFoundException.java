package io.github.calegrandy.askdb.exception;

public class QueryNotFoundException extends RuntimeException {

    public QueryNotFoundException(String message) {
        super(message);
    }
}
