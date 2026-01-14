package br.com.protbike.exceptions;

public class NonRetryableException extends RuntimeException {

    public NonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRetryableException(String message) {
        super(message);
    }
}
