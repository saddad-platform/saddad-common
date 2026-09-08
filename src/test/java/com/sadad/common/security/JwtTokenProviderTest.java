package com.sadad.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "SADAD_ENTERPRISE_SECRET_KEY_FOR_TESTING_PURPOSES_2026";
    private final long expirationMs = 3600000;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(secret, expirationMs);
    }

    @Test
    void testTokenCreationAndValidation() {
        String token = tokenProvider.createToken("admin@rawabi.sa", "rawabi-logistics", "1010574823", List.of("CORPORATE_ADMIN"));

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals("admin@rawabi.sa", tokenProvider.getUsername(token));
        assertEquals("rawabi-logistics", tokenProvider.getTenantId(token));
        assertEquals("1010574823", tokenProvider.getCrNumber(token));
    }

    @Test
    void testInvalidToken() {
        assertFalse(tokenProvider.validateToken("invalid.bearer.token"));
    }
}