package vn.edu.hust.kat.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import vn.edu.hust.kat.apigateway.filter.AuthFilter;
import vn.edu.hust.kat.apigateway.util.JwtUtil;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] OPEN_ENDPOINTS = {
            "/api/v1/user-service/auth/**"
    };

    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .anyExchange().permitAll()
                )
                .build();
    }
}