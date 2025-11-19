package com.bookticket.api_gateway.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
@Component
public class RateLimitConfig {
    private double tokensPerSecond = 1.66; // 1.66 tokens per second
    private int tokensPerMinute = 100; // 100 tokens per minute
    private int bucketCapacity = 100; // Maximum tokens in bucket
}

