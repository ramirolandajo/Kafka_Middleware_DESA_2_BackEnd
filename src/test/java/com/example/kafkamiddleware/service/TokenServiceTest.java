package com.example.kafkamiddleware.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void setupKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
    }

    private String createTestToken(Map<String, Object> claims) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        claims.forEach(builder::claim);
        if (!claims.containsKey("exp")) {
            builder.expirationTime(new Date(System.currentTimeMillis() + 60_000));
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), builder.build());
        JWSSigner signer = new RSASSASigner((RSAPrivateKey) keyPair.getPrivate());
        jwt.sign(signer);
        return jwt.serialize();
    }

    @Test
    void validateAndExtractClientId_missingToken_throws() {
        TokenService svc = new TokenService();
        TokenService.TokenValidationException e = assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId(null));
        assertEquals("Authorization token missing", e.getMessage());
    }

    @Test
    void validateAndExtractClientId_expiredToken_throws() throws Exception {
        long expiredTime = System.currentTimeMillis() - 1000;
        String token = createTestToken(Map.of("azp", "c1", "exp", new Date(expiredTime)));

        TokenService svc = new TokenService();
        TokenService.TokenValidationException e = assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId("Bearer " + token));
        assertEquals("Token expired", e.getMessage());
    }

    @Test
    void validateAndExtractClientId_validToken_returnsAzp() throws Exception {
        String token = createTestToken(Map.of("azp", "my-client"));
        TokenService svc = new TokenService();
        String clientId = svc.validateAndExtractClientId("Bearer " + token);
        assertEquals("my-client", clientId);
    }

    @Test
    void validateAndExtractClientId_noBearerPrefix_stillWorks() throws Exception {
        String token = createTestToken(Map.of("azp", "my-client"));
        TokenService svc = new TokenService();
        String clientId = svc.validateAndExtractClientId(token);
        assertEquals("my-client", clientId);
    }

    @Test
    void validateAndExtractClientId_malformedToken_throws() {
        TokenService svc = new TokenService();
        TokenService.TokenValidationException e = assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId("not-a-jwt"));
        assertEquals("Malformed token", e.getMessage());
    }

    @Test
    void validateAndExtractClientId_noExpiry_throws() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("sub").build(); // No exp
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        String token = jwt.serialize();

        TokenService svc = new TokenService();
        TokenService.TokenValidationException e = assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId(token));
        assertEquals("Token has no expiration", e.getMessage());
    }

    @Test
    void validateAndExtractClientId_fromClientIdClaim() throws Exception {
        String token = createTestToken(Map.of("client_id", "client-from-claim"));
        TokenService svc = new TokenService();
        assertEquals("client-from-claim", svc.validateAndExtractClientId(token));
    }

    @Test
    void validateAndExtractClientId_fromSubject() throws Exception {
        String token = createTestToken(Map.of("sub", "client-from-subject"));
        TokenService svc = new TokenService();
        assertEquals("client-from-subject", svc.validateAndExtractClientId(token));
    }

    @Test
    void validateAndExtractClientId_noClientIdFound_throws() throws Exception {
        // Token with only an expiration date
        String token = createTestToken(Map.of());
        TokenService svc = new TokenService();
        TokenService.TokenValidationException e = assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId(token));
        assertEquals("Unable to extract client id from token", e.getMessage());
    }
}
