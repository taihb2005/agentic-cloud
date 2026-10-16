package vn.edu.hust.kat.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${spring.application.name}")
    private String appName;

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("Spring Boot Starter Kit API")
                .description("""
                    🚀 Production-ready Spring Boot REST API template.
                    
                    **Features:**
                    - JWT Authentication (Access + Refresh tokens)
                    - Role-based access control (USER, ADMIN)
                    - Rate limiting per IP (Bucket4j)
                    - Request ID tracking via MDC
                    - Global exception handling
                    - Flyway database migrations
                    
                    **Auth Flow:**
                    1. Register → `POST /api/v1/user/service/auth/register`
                    2. Login → `POST /api/v1/user-service/auth/login` → get `accessToken`
                    3. Add header: `Authorization: Bearer <accessToken>`
                    4. Refresh → `POST /api/v1/user-service/auth/refresh`
                    """)
                .version("1.0.0")
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url("http://localhost:8081").description("Local Development"),
                new Server().url("https://api.yourdomain.com").description("Production")
            ))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Enter JWT token. Obtain it from POST /api/v1/user-service/auth/login")));
    }
}
