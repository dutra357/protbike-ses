package br.com.protbike.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "notificacao.email.faulttolerance")
public record EmailFaultToleranceConfig(
        Retry retry,
        Timeout timeout,
        CircuitBreaker circuitBreaker,
        RateLimit rateLimit
) {
    public record Retry(int maxRetries, long delayMs) {}
    public record Timeout(long durationMs) {}
    public record CircuitBreaker(int requestVolumeThreshold, double failureRatio, long delayMs) {}
    public record RateLimit(int limit, long windowSeconds) {}
}

