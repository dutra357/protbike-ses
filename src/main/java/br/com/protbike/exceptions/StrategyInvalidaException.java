package br.com.protbike.exceptions;

public class StrategyInvalidaException extends RuntimeException {
    public StrategyInvalidaException(String message) {
        super(message);
    }
}
