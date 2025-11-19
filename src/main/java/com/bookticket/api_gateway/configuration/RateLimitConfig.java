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
    private double tokensPerSecond;
    private int tokensPerMinute;
    private int bucketCapacity;
    private String prefix;
    private boolean enabled;
}

