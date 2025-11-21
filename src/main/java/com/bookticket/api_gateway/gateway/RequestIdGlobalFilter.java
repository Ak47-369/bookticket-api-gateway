package com.bookticket.api_gateway.gateway;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // Run before any other filter
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get Id from header or Generate a new one
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;

        // Mutate The Request to pass the header DOWNSTREAM to microservices
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(REQUEST_ID_HEADER, finalRequestId)
                        .build())
                .build();

        MDC.put(MDC_KEY, finalRequestId); // Add to MDC for gateway logs
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, finalRequestId); // Add to response headers so client can see it
        return chain.filter(modifiedExchange)
                .doFinally(signalType -> MDC.clear()); // Cleanup
    }
}
