package vn.edu.hust.kat.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.hust.kat.apigateway.filter.AuthFilter;
import vn.edu.hust.kat.apigateway.util.JwtUtil;

@Configuration
@RequiredArgsConstructor
public class RouteConfig {
    private final JwtUtil jwtUtil;

    private static final String[] USER_PUBLIC_ENDPOINTS = {
            "/api/v1/user-service/auth/**",
            "/api/v1/user-service/swagger-ui.html",
            "/api/v1/user-service/swagger-ui/**",
            "/api/v1/user-service/v3/api-docs/**"
    };

    private static final String[] USER_AUTHENTICATED_ENDPOINTS = {
            "/api/v1/user-service/**"
    };

    private static final String[] STORAGE_AUTHENTICATED_ENDPOINTS = {
            "/api/v1/storage-service/**"
    };

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

                // =========================
                // User Service - Public
                // =========================
                .route("user-public", r -> r
                        .path(USER_PUBLIC_ENDPOINTS)
                        .filters(f -> f
                                .stripPrefix(2))
                        .uri("http://user-service:8081"))

                // =========================
                // User Service - Authenticated
                // =========================
                .route("user-authenticated", r -> r
                        .path(USER_AUTHENTICATED_ENDPOINTS)
                        .filters(f -> f
                                .filter(authFilter())
                                .stripPrefix(2))
                        .uri("http://user-service:8081"))

                // =========================
                // Storage Service - Authenticated
                // =========================
                .route("storage-authenticated", r -> r
                        .path(STORAGE_AUTHENTICATED_ENDPOINTS)
                        .filters(f -> f
                                .filter(authFilter())
                                .stripPrefix(2))
                        .uri("http://storage-service:8082"))

                .build();
    }

    @Bean
    public AuthFilter authFilter() {
        return new AuthFilter(jwtUtil);
    }
}
