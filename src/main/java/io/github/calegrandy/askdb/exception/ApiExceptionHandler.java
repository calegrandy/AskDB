package io.github.calegrandy.askdb.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({ConnectionNotFoundException.class, QueryNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    // A service AskDB depends on (the model API or the queried database) failed.
    @ExceptionHandler(QueryFailedException.class)
    public ResponseEntity<ErrorResponse> handleQueryFailed(QueryFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(HttpStatus.BAD_GATEWAY.value(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(ClassCastException.class)
    public ResponseEntity<ErrorResponse> handleClassCast(ClassCastException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid value type: " + ex.getMessage()));
    }
}
