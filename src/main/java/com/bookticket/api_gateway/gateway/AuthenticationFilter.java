package com.bookticket.api_gateway.gateway;

import com.bookticket.api_gateway.configuration.JwtUtils;
import io.jsonwebtoken.Claims;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final RouterValidator routerValidator;
    private final JwtUtils jwtUtil;

    @Autowired
    public AuthenticationFilter(RouterValidator routerValidator, JwtUtils jwtUtil) {
        this.routerValidator = routerValidator;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public int getOrder() {
        return -1; // Highest Priority, Run this filter before any other filter
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        log.info("Request URI: {}", request.getURI());
        if (routerValidator.isSecured.test(request)) {
            if (!request.getHeaders().containsKey("Authorization")) {
                log.error("Authorization header is missing");
                return this.onError(exchange, HttpStatus.UNAUTHORIZED);
            }
            String authHeader = request.getHeaders().get("Authorization").get(0);
            if(!authHeader.startsWith("Bearer ")) {
                log.error("Authorization header is not valid");
                return this.onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                log.error("JWT token is not valid");
                return this.onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            Claims claims = jwtUtil.extractAllClaims(token);
            String userId = claims.getSubject().toString();
            String roles = jwtUtil.extractRoles(claims);
            String username = claims.get("username").toString();

            if (!roles.contains("ADMIN") && !roles.contains("USER")) {
                log.error("User does not have the required role");
                return this.onError(exchange, HttpStatus.FORBIDDEN);
            }

            ServerHttpRequest newRequest = request.mutate()
                    .header("X-User-ID", userId) // Immutable User ID
                    .header("X-User-Roles", roles)
                    .header("X-User-Name", username)
                    .build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        }
        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        log.error("Error in Gateway: {}", httpStatus);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }
}
