package vn.edu.hust.kat.userservice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.userservice.entity.CloudUserDetails;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String TOKEN_TYPE = "token_type";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    public String generateToken(CloudUserDetails userDetails, TokenType tokenType) {
        long tokenExpiry = (tokenType == TokenType.ACCESS_TOKEN) ? accessTokenExpiry : refreshTokenExpiry;
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_ROLES, roles);
        claims.put(TOKEN_TYPE, tokenType.toString().toLowerCase());

        return buildToken(claims, userDetails, tokenExpiry);
    }

    private String buildToken(Map<String, Object> claims, CloudUserDetails userDetails, long expiry) {
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUserId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getSigningKey())
                .compact();
    }


    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token); // throws if invalid/expired
            return !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }


    @SuppressWarnings("unchecked")
    public List<GrantedAuthority> extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        if (roles == null) return List.of();
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public enum TokenType {
        ACCESS_TOKEN,
        REFRESH_TOKEN
    }
}