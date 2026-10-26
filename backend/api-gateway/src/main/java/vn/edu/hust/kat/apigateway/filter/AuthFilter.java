package vn.edu.hust.kat.apigateway.filter;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cloud.gateway.filter.GatewayFilter;
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
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import vn.edu.hust.kat.apigateway.util.JwtUtil;

import java.util.List;

@Slf4j
public class AuthFilter implements GatewayFilter {

    private final JwtUtil jwtUtil;

    public AuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.info("Request: {} {}", exchange.getRequest().getMethod(), path);

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

}