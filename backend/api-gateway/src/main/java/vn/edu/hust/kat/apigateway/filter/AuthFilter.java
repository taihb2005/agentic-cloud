package vn.edu.hust.kat.apigateway.filter;

import io.jsonwebtoken.Claims;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.edu.hust.kat.apigateway.util.JwtUtil;

import java.util.List;

@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private static final Logger logger = LogManager.getLogger(AuthFilter.class);

    private final JwtUtil jwtUtil;

    // List of public API does not need to authorization
    private static final List<String> OPEN_ENDPOINTS = List.of(
            "/api/v1/user-service/auth/login",
            "/api/v1/user-service/auth/register",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    public AuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        logger.info("Request: {} {}", exchange.getRequest().getMethod(), path);

        // Skip public endpoints
        if (OPEN_ENDPOINTS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // Check authorization header
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            return Mono.error(
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase())
            );
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase())
            );
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtUtil.isTokenValid(token)) {
                return Mono.error(
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase())
                );
            }

            Claims claims = jwtUtil.extractAllClaims(token);
            String userId = claims.getSubject(); // User id from subject

            // Extract roles from JWT claims and forward as header
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            String rolesHeader = (roles != null) ? String.join(",", roles) : "";

            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", rolesHeader)
                    .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            // Invalid token, throw error
            return Mono.error(
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), e)
            );
        }
    }


    @Override
    public int getOrder() {
        return -1; // Top-order filter
    }
}