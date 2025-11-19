package com.bookticket.api_gateway.ratelimit;

import com.bookticket.api_gateway.configuration.RateLimitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class RedisRateLimiterService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RateLimitConfig rateLimitConfig;
    
    // Lua script for atomic token bucket rate limiting
    private static final String RATE_LIMIT_SCRIPT = 
        "local key = KEYS[1]\n" +
        "local capacity = tonumber(ARGV[1])\n" +
        "local tokens_per_second = tonumber(ARGV[2])\n" +
        "local requested_tokens = tonumber(ARGV[3])\n" +
        "local now = tonumber(ARGV[4])\n" +
        "\n" +
        "local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')\n" +
        "local tokens = tonumber(bucket[1])\n" +
        "local last_refill = tonumber(bucket[2])\n" +
        "\n" +
        "if tokens == nil then\n" +
        "  tokens = capacity\n" +
        "  last_refill = now\n" +
        "end\n" +
        "\n" +
        "-- Calculate tokens to add based on time elapsed\n" +
        "local time_elapsed = now - last_refill\n" +
        "local tokens_to_add = time_elapsed * tokens_per_second\n" +
        "tokens = math.min(capacity, tokens + tokens_to_add)\n" +
        "last_refill = now\n" +
        "\n" +
        "local allowed = 0\n" +
        "if tokens >= requested_tokens then\n" +
        "  tokens = tokens - requested_tokens\n" +
        "  allowed = 1\n" +
        "end\n" +
        "\n" +
        "redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)\n" +
        "redis.call('EXPIRE', key, 120)\n" +
        "\n" +
        "return {allowed, tokens}";

    private final RedisScript<List> rateLimitScript;

    public RedisRateLimiterService(ReactiveRedisTemplate<String, String> redisTemplate, 
                                   RateLimitConfig rateLimitConfig) {
        this.redisTemplate = redisTemplate;
        this.rateLimitConfig = rateLimitConfig;
        this.rateLimitScript = RedisScript.of(RATE_LIMIT_SCRIPT, List.class);
    }

    /**
     * Check if the request is allowed based on token bucket rate limiting
     * @param key The rate limit key (e.g., user ID or IP address)
     * @return Mono<Boolean> true if allowed, false if rate limited
     */
    public Mono<RateLimitResult> isAllowed(String key) {
        return isAllowed(key, 1);
    }

    /**
     * Check if the request is allowed based on token bucket rate limiting
     * @param key The rate limit key (e.g., user ID or IP address)
     * @param requestedTokens Number of tokens to consume
     * @return Mono<RateLimitResult> containing whether request is allowed and remaining tokens
     */
    public Mono<RateLimitResult> isAllowed(String key, int requestedTokens) {
        String redisKey = "rate_limit:" + key;
        long now = System.currentTimeMillis() / 1000; // Current time in seconds
        
        List<String> keys = Arrays.asList(redisKey);
        List<String> args = Arrays.asList(
            String.valueOf(rateLimitConfig.getBucketCapacity()),
            String.valueOf(rateLimitConfig.getTokensPerSecond()),
            String.valueOf(requestedTokens),
            String.valueOf(now)
        );

        return redisTemplate.execute(rateLimitScript, keys, args)
            .map(result -> {
                if (result != null && result.size() >= 2) {
                    Long allowed = (Long) result.get(0);
                    Double remainingTokens = Double.parseDouble(result.get(1).toString());
                    boolean isAllowed = allowed == 1;
                    
                    log.debug("Rate limit check for key {}: allowed={}, remaining tokens={}", 
                             key, isAllowed, remainingTokens);
                    
                    return new RateLimitResult(isAllowed, remainingTokens);
                }
                log.warn("Unexpected result from rate limit script for key {}", key);
                return new RateLimitResult(false, 0.0);
            })
            .onErrorResume(e -> {
                log.error("Error checking rate limit for key {}: {}", key, e.getMessage());
                // Fail open - allow request if Redis is down
                return Mono.just(new RateLimitResult(true, 0.0));
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

