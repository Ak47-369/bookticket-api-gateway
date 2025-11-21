package com.bookticket.api_gateway.ratelimit;

import com.bookticket.api_gateway.configuration.JwtUtils;
import com.bookticket.api_gateway.gateway.RouterValidator;
import com.bookticket.api_gateway.ratelimit.RedisRateLimiterService;
import lombok.extern.slf4j.Slf4j;
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
    private final JwtUtils jwtUtils;
    private final RouterValidator routerValidator;

    public RateLimitingFilter(RedisRateLimiterService rateLimiterService,
                             JwtUtils jwtUtils,
                             RouterValidator routerValidator) {
        this.rateLimiterService = rateLimiterService;
        this.jwtUtils = jwtUtils;
        this.routerValidator = routerValidator;
    }

    @Override
    public int getOrder() {
        return -2; // Run before AuthenticationFilter
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Check if Rate Limiting is enabled or not
        if (!rateLimiterService.isEnabled()) {
            return chain.filter(exchange);
        }
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
     * Priority:
     * 1. X-User-ID header (for inter-service requests that already have user context)
     * 2. Extract from JWT token (for direct user requests)
     * 3. IP address (for unauthenticated requests)
     */
    private String getRateLimitKey(ServerHttpRequest request) {
        // Check if user ID is available in header (from inter-service requests)
        String userId = request.getHeaders().getFirst("X-User-ID");
        if (userId != null && !userId.isEmpty()) {
            log.debug("Using X-User-ID from header for rate limiting: {}", userId);
            return "user:" + userId;
        }

        // Try to extract user ID from JWT token for authenticated requests
        if (routerValidator.isSecured.test(request)) {
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                userId = jwtUtils.extractUserId(token);
                if (userId != null && !userId.isEmpty()) {
                    log.debug("Extracted user ID from JWT token for rate limiting: {}", userId);
                    return "user:" + userId;
                }
            }
        }

        // Fallback to IP address for unauthenticated requests or if JWT extraction fails
        String ipAddress = getClientIpAddress(request);
        log.debug("Using IP address for rate limiting: {}", ipAddress);
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

