package vn.edu.hust.kat.storageservice.utils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ShareTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_BYTE_LENGTH = 24; // 192 bits -> ~32 characters Base64 URL-safe

    public String generateToken() {
        byte[] randomBytes = new byte[DEFAULT_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
