package vn.edu.hust.kat.agentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filter that trusts the X-User-Id header set by the API Gateway after JWT validation.
 * <p>
 * Flow: Client → API Gateway (validates JWT, extracts userId, sets X-User-Id header)
 *       → this filter reads X-User-Id and populates SecurityContext.
 * This service does not hold user id like other. It must call user service to indicate that if this user actually exists
 * <p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        List<String> roles = Collections.list(request.getHeaders(USER_ROLES));

        if (userId == null || userId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only set authentication if not already set
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID uid = UUID.fromString(userId);

                List<GrantedAuthority> authorities = roles.stream()
                        .filter(role -> role != null && !role.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UserPrincipal userPrincipal = UserPrincipal.builder()
                        .userId(uid)
                        .authorities(authorities)
                        .build();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user via Gateway header: userId={}, role={}", userId, roles);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in X-User-Id header: {}", userId);
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }
}
