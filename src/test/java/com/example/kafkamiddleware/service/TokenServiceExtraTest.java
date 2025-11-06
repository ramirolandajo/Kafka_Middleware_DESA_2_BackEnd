package com.example.kafkamiddleware.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceExtraTest {

    @Test
    void validateWithJwks_noMatchingKid_throws() throws Exception {
        // Generate RSA key for JWKS with kid "k-other"
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();

        RSAKey jwk = new RSAKey.Builder(pub).keyID("k-other").build();
        JWKSet jwkSet = new JWKSet(jwk);

        File tmp = File.createTempFile("jwks-no-kid", ".json");
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) { w.write(jwkSet.toJSONObject().toString()); }

        // Build signed JWT with kid "k1"
        KeyPairGenerator kpg2 = KeyPairGenerator.getInstance("RSA");
        kpg2.initialize(2048);
        KeyPair kp2 = kpg2.generateKeyPair();
        RSAPrivateKey priv2 = (RSAPrivateKey) kp2.getPrivate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub")
                .claim("azp", "clientNoMatch")
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
        JWSSigner signer = new RSASSASigner(priv2);
        jwt.sign(signer);

        TokenService svc = new TokenService();
        java.lang.reflect.Field f = TokenService.class.getDeclaredField("jwksUri");
        f.setAccessible(true);
        f.set(svc, tmp.toURI().toString());

        assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId("Bearer " + jwt.serialize()));
    }

    @Test
    void validateWithJwks_cacheExpired_reloadFromUri() throws Exception {
        // Create a JWKS file with a key k-new which will match the JWT
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

        RSAKey jwkNew = new RSAKey.Builder(pub).keyID("k-new").build();
        JWKSet jwkSetNew = new JWKSet(jwkNew);

        File tmp = File.createTempFile("jwks-new", ".json");
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) { w.write(jwkSetNew.toJSONObject().toString()); }

        // Build signed JWT using k-new
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub")
                .claim("azp", "clientReloaded")
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k-new").build(), claims);
        JWSSigner signer = new RSASSASigner(priv);
        jwt.sign(signer);

        TokenService svc = new TokenService();
        // seed cache with a different JWKSet and old fetchedAt
        java.lang.reflect.Field cacheField = TokenService.class.getDeclaredField("cachedJwkSet");
        cacheField.setAccessible(true);
        cacheField.set(svc, new JWKSet());

        java.lang.reflect.Field fetchedAtField = TokenService.class.getDeclaredField("jwkSetFetchedAt");
        fetchedAtField.setAccessible(true);
        // force expiry: jwkCacheTtl = 10 * 60 * 1000L
        long expired = System.currentTimeMillis() - (10 * 60 * 1000L) - 1000L;
        fetchedAtField.setLong(svc, expired);

        java.lang.reflect.Field f = TokenService.class.getDeclaredField("jwksUri");
        f.setAccessible(true);
        f.set(svc, tmp.toURI().toString());

        // Should reload the JWKS from file and validate signature
        String clientId = svc.validateAndExtractClientId("Bearer " + jwt.serialize());
        assertEquals("clientReloaded", clientId);
    }
}

