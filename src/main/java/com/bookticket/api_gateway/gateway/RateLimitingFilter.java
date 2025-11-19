package com.bookticket.api_gateway.gateway;

import com.bookticket.api_gateway.ratelimit.RedisRateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@Slf4j
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiterService rateLimiterService;

    @Autowired
    public RateLimitingFilter(RedisRateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public int getOrder() {
        return -2; // Run before AuthenticationFilter (which has order -1)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Get rate limit key - prefer user ID from header, fallback to IP address
        String rateLimitKey = getRateLimitKey(request);
        
        log.debug("Checking rate limit for key: {}", rateLimitKey);
        
        return rateLimiterService.isAllowed(rateLimitKey)
            .flatMap(result -> {
                if (result.isAllowed()) {
                    // Add rate limit headers to response
                    ServerHttpResponse response = exchange.getResponse();
                    response.getHeaders().add("X-RateLimit-Remaining", 
                                             String.format("%.2f", result.getRemainingTokens()));
                    
                    log.debug("Request allowed for key: {}, remaining tokens: {}", 
                             rateLimitKey, result.getRemainingTokens());
                    
                    return chain.filter(exchange);
                } else {
                    log.warn("Rate limit exceeded for key: {}", rateLimitKey);
                    return onRateLimitExceeded(exchange);
                }
            });
    }

    /**
     * Get the rate limit key from the request
     * Priority: X-User-ID header > IP address
     */
    private String getRateLimitKey(ServerHttpRequest request) {
        // Check if user ID is available in header (from previous authenticated requests)
        String userId = request.getHeaders().getFirst("X-User-ID");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }
        
        // Fallback to IP address
        String ipAddress = getClientIpAddress(request);
        return "ip:" + ipAddress;
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(ServerHttpRequest request) {
        // Check X-Forwarded-For header first (for requests behind proxy/load balancer)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, get the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to remote address
        return Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
    }

    /**
     * Handle rate limit exceeded
     */
    private Mono<Void> onRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("X-RateLimit-Retry-After", "60"); // Retry after 60 seconds
        response.getHeaders().add("Content-Type", "application/json");
        
        String errorMessage = "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please try again later.\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorMessage.getBytes())));
    }
}

