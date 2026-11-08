package vn.edu.hust.kat.storageservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vn.edu.hust.kat.storageservice.entity.FsNode;
import vn.edu.hust.kat.storageservice.exception.InvalidSharePasswordException;
import vn.edu.hust.kat.storageservice.exception.PasswordRequiredException;
import vn.edu.hust.kat.storageservice.repository.FsNodeRepository;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
public class PublicShareTokenService {

    private static final String PREFIX = "pub_sess_";
    private final String jwtSecret;

    public PublicShareTokenService(
            @Value("${app.jwt.share-secret:SmartCloudPublicShareJwtSecretKeyMustBeAtLeast256BitsLongForHS256Security}")
            String jwtSecret
    ) {
        this.jwtSecret = jwtSecret;
    }

    public String generatePublicAccessToken(String shareToken, Long nodeId, String permission, long expiresInSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiresInSeconds * 1000L);

        String compactJwt = Jwts.builder()
                .subject(shareToken)
                .claim("shareToken", shareToken)
                .claim("nodeId", nodeId)
                .claim("permission", permission)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();

        return PREFIX + compactJwt;
    }

    public String generateUserAccessToken(UUID userId, Long nodeId, String permission, long expiresInSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiresInSeconds * 1000L);

        return Jwts.builder()
                .subject(userId != null ? userId.toString() : "")
                .claim("userId", userId != null ? userId.toString() : "")
                .claim("nodeId", nodeId)
                .claim("node_id", nodeId)
                .claim("permission", permission)
                .claim("appliesToDescendants", true)
                .claim("applies_to_descendants", true)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims validateAndExtractClaims(String rawToken, String expectedShareToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AccessDeniedException("Tệp hoặc thư mục này được bảo vệ bằng mật khẩu. Vui lòng nhập mật khẩu để tiếp tục.");
        }

        String token = rawToken.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        if (token.startsWith(PREFIX)) {
            token = token.substring(PREFIX.length()).trim();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenShareToken = claims.get("shareToken", String.class);
            if (tokenShareToken == null) {
                tokenShareToken = claims.getSubject();
            }

            if (expectedShareToken != null && !expectedShareToken.equals(tokenShareToken)) {
                throw new AccessDeniedException("PERMISSION_DENIED: Token phiên không hợp lệ cho liên kết chia sẻ này");
            }

            return claims;
        } catch (ExpiredJwtException e) {
            log.warn("Public share token expired: {}", e.getMessage());
            throw new AccessDeniedException("PERMISSION_DENIED: Phiên truy cập liên kết công khai đã hết hạn. Vui lòng mở khóa lại");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid public share token: {}", e.getMessage());
            throw new AccessDeniedException("PERMISSION_DENIED: Token phiên không hợp lệ hoặc đã bị thay đổi");
        }
    }

    public boolean isNodeUnlockedByUserToken(String rawToken, UUID userId, FsNode targetNode, Long unlockNodeId, FsNodeRepository fsNodeRepository) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String token = rawToken.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenUserId = claims.get("userId", String.class);
            if (tokenUserId == null) {
                tokenUserId = claims.getSubject();
            }
            if (userId != null && !userId.toString().equals(tokenUserId)) {
                return false;
            }

            Long tokenNodeId = null;
            Object rawNodeId = claims.get("nodeId");
            if (rawNodeId == null) {
                rawNodeId = claims.get("node_id");
            }
            if (rawNodeId instanceof Number) {
                tokenNodeId = ((Number) rawNodeId).longValue();
            } else if (rawNodeId instanceof String) {
                try {
                    tokenNodeId = Long.parseLong((String) rawNodeId);
                } catch (NumberFormatException ignored) {}
            }

            if (tokenNodeId == null) {
                return false;
            }

            if (unlockNodeId != null && unlockNodeId.equals(tokenNodeId)) {
                return true;
            }

            if (targetNode != null) {
                FsNode curr = targetNode;
                while (curr != null) {
                    if (tokenNodeId.equals(curr.getId())) {
                        return true;
                    }
                    curr = curr.getParent();
                }
                if (fsNodeRepository != null && targetNode.getId() != null) {
                    return fsNodeRepository.isDescendant(targetNode.getId(), tokenNodeId);
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("User share token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isNodeUnlockedByUserToken(String rawToken, UUID userId, Long targetNodeId, Long unlockNodeId, FsNodeRepository fsNodeRepository) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String token = rawToken.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenUserId = claims.get("userId", String.class);
            if (tokenUserId == null) {
                tokenUserId = claims.getSubject();
            }
            if (userId != null && !userId.toString().equals(tokenUserId)) {
                return false;
            }

            Long tokenNodeId = null;
            Object rawNodeId = claims.get("nodeId");
            if (rawNodeId == null) {
                rawNodeId = claims.get("node_id");
            }
            if (rawNodeId instanceof Number) {
                tokenNodeId = ((Number) rawNodeId).longValue();
            } else if (rawNodeId instanceof String) {
                try {
                    tokenNodeId = Long.parseLong((String) rawNodeId);
                } catch (NumberFormatException ignored) {}
            }

            if (tokenNodeId == null) {
                return false;
            }

            if (unlockNodeId != null && unlockNodeId.equals(tokenNodeId)) {
                return true;
            }
            if (targetNodeId != null && targetNodeId.equals(tokenNodeId)) {
                return true;
            }
            if (fsNodeRepository != null && targetNodeId != null) {
                return fsNodeRepository.isDescendant(targetNodeId, tokenNodeId);
            }
            return false;
        } catch (Exception e) {
            log.debug("User share token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                Base64.getEncoder().encodeToString(jwtSecret.getBytes(StandardCharsets.UTF_8))
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
