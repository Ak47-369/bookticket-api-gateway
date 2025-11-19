package com.bookticket.api_gateway.ratelimit;

import com.bookticket.api_gateway.configuration.RateLimitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Redis-based rate limiter service using Token Bucket algorithm.
 * Provides distributed rate limiting across multiple gateway instances.
 * Uses synchronous RedisTemplate wrapped in Mono for reactive compatibility.
 */
@Service
@Slf4j
public class RedisRateLimiterService {

    private static final int REQUESTED_TOKENS = 1;

    private final RedisTemplate<String, String> scriptRedisTemplate;
    private final RedisScript<List> rateLimitScript;
    private final RateLimitConfig rateLimitConfig;

    public RedisRateLimiterService(
            @Qualifier("scriptRedisTemplate") RedisTemplate<String, String> scriptRedisTemplate,
            @Qualifier("rateLimitScript") RedisScript<List> rateLimitScript,
            RateLimitConfig rateLimitConfig) {
        this.scriptRedisTemplate = scriptRedisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.rateLimitConfig = rateLimitConfig;
        log.info("RedisRateLimiterService initialized with config: {} tokens/sec, {} bucket capacity",
                rateLimitConfig.getTokensPerSecond(), rateLimitConfig.getBucketCapacity());
    }

    /**
     * Check if the request is allowed based on token bucket rate limiting.
     * Uses default of 1 token per request.
     *
     * @param key The rate limit key (e.g., "user:123" or "ip:192.168.1.1")
     * @return Mono<RateLimitResult> containing whether request is allowed and remaining tokens
     */
    public Mono<RateLimitResult> isAllowed(String key) {
        return isAllowed(key, REQUESTED_TOKENS);
    }

    public boolean isEnabled() {
        return rateLimitConfig.isEnabled();
    }

    /**
     * Check if the request is allowed based on token bucket rate limiting.
     * Executes Lua script synchronously and wraps result in Mono for reactive compatibility.
     *
     * @param key The rate limit key (e.g., "user:123" or "ip:192.168.1.1")
     * @param requestedTokens Number of tokens to consume
     * @return Mono<RateLimitResult> containing whether request is allowed and remaining tokens
     */
    public Mono<RateLimitResult> isAllowed(String key, int requestedTokens) {
        return Mono.fromCallable(() -> {
            String redisKey = rateLimitConfig.getPrefix() + ":" + key;
            long nowInSeconds = Instant.now().getEpochSecond();

            try {
                List result = scriptRedisTemplate.execute(
                        rateLimitScript,
                        Collections.singletonList(redisKey),
                        String.valueOf(rateLimitConfig.getBucketCapacity()),
                        String.valueOf(rateLimitConfig.getTokensPerSecond()),
                        String.valueOf(requestedTokens),
                        String.valueOf(nowInSeconds)
                );

                if (result != null && result.size() >= 2) {
                    long allowed = ((Number) result.get(0)).longValue();
                    double remainingTokens = ((Number) result.get(1)).doubleValue();
                    boolean isAllowed = allowed == 1;

                    if (isAllowed) {
                        log.debug("Rate limit check for key '{}': ALLOWED, remaining tokens: {:.2f}",
                                key, remainingTokens);
                    } else {
                        log.warn("Rate limit check for key '{}': EXCEEDED, remaining tokens: {:.2f}",
                                key, remainingTokens);
                    }

                    return new RateLimitResult(isAllowed, remainingTokens);
                }

                log.warn("Unexpected result from rate limit script for key '{}': {}", key, result);
                return new RateLimitResult(false, 0.0);

            } catch (Exception e) {
                log.error("Error executing rate limiter script for key '{}'. Failing open (allowing request). Error: {}",
                        key, e.getMessage(), e);
                // Fail open - allow request if Redis is unavailable
                return new RateLimitResult(true, rateLimitConfig.getBucketCapacity());
            }
        });
    }

    /**
     * Result of rate limit check
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final double remainingTokens;

        public RateLimitResult(boolean allowed, double remainingTokens) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public double getRemainingTokens() {
            return remainingTokens;
        }
    }
}

