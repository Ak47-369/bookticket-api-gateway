# BookTicket :: Platform :: API Gateway

## Overview

The **API Gateway** is the primary entry point for all external traffic into the BookTicket microservices ecosystem. It serves as a reverse proxy, routing client requests to the appropriate internal services. It is also responsible for handling critical cross-cutting concerns, providing a secure, reliable, and unified interface for our frontend applications.

This service is built using **Spring Cloud Gateway**.

## Core Responsibilities

-   **Request Routing:** Intelligently routes incoming HTTP requests to the correct downstream microservice based on path predicates.
-   **Service Discovery Integration:** Dynamically discovers service locations by integrating with the Eureka Service Registry, enabling resilience and scalability.
-   **Centralized Authentication:** Validates JWTs for all secured endpoints, enriching requests with user context before forwarding them.
-   **Rate Limiting:** Protects the system from abuse by enforcing rate limits on incoming requests using a Redis-backed token bucket algorithm.
-   **Unified API Documentation:** Aggregates OpenAPI (Swagger) documentation from all downstream services into a single, convenient UI.

## Architecture & Request Flow
<img width="500" height="1500" alt="API-Gateway-Flow" src="https://github.com/user-attachments/assets/8f7c6825-292a-4169-a3dd-b6ef3a8b59a7" />


### How It Works

1.  **Dynamic Routing:** The gateway uses route definitions from the `api-gateway-prod.yaml` file (managed by the Config Server). It uses the `lb://` scheme to look up service locations from Eureka, allowing for dynamic scaling and resilience.
2.  **Global Filters:** All incoming requests pass through a chain of `GlobalFilter` implementations:
    -   **`RateLimitingFilter`:** Checks if the client (identified by IP or user ID) has exceeded their allowed request rate.
    -   **`AuthenticationFilter`:** For protected routes, this filter validates the JWT from the `Authorization` header. If valid, it adds `X-User-ID`, `X-User-Name`, and `X-User-Roles` headers to the request for downstream services to consume.
3.  **Load Balancing:** When routing to a service, the gateway's built-in Spring Cloud LoadBalancer chooses a healthy instance from the list provided by Eureka.

## Key Dependencies

-   **Spring Cloud Gateway:** The core non-blocking, reactive gateway framework.
-   **Eureka Discovery Client:** For integrating with the service registry.
-   **Spring Boot Starter Data Redis Reactive:** Used for the Redis-backed rate limiting implementation.
-   **JJWT (Java JWT):** For parsing and validating JSON Web Tokens.
-   **SpringDoc OpenAPI WebFlux UI:** To aggregate and display Swagger documentation from all microservices.

## Configuration

The gateway's configuration, including its routes, is managed by the **Config Server**. Key properties are defined in `api-gateway-prod.yaml`.

-   **Routes:** Defined under `spring.cloud.gateway.routes`, mapping URL paths to service IDs (e.g., `lb://user-service`).
-   **Documentation:** The `springdoc.swagger-ui.urls` property is used to define the list of microservices to include in the combined documentation view.
