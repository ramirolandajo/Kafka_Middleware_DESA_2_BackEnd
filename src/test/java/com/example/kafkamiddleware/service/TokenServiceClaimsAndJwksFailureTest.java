package com.example.kafkamiddleware.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceClaimsAndJwksFailureTest {

    @Test
    void validate_extracts_client_id_claim() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("subj")
                .claim("client_id", "clientCID")
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        JWSSigner signer = new RSASSASigner(priv);
        jwt.sign(signer);

        TokenService svc = new TokenService();
        String cid = svc.validateAndExtractClientId("Bearer " + jwt.serialize());
        assertEquals("clientCID", cid);
    }

    @Test
    void validate_extracts_clientId_camelCase_claim() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("subj2")
                .claim("clientId", "clientCamel")
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        JWSSigner signer = new RSASSASigner(priv);
        jwt.sign(signer);

        TokenService svc = new TokenService();
        String cid = svc.validateAndExtractClientId("Bearer " + jwt.serialize());
        assertEquals("clientCamel", cid);
    }

    @Test
    void validate_withJwksUri_inaccessible_throws() throws Exception {
        TokenService svc = new TokenService();
        java.lang.reflect.Field f = TokenService.class.getDeclaredField("jwksUri");
        f.setAccessible(true);
        // point to a non-existent file to force IOException
        f.set(svc, "file:///this/file/does/not/exist.jwks");

        // build a minimal valid jwt
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("s")
                .claim("azp", "c")
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        JWSSigner signer = new RSASSASigner(priv);
        jwt.sign(signer);

        assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId("Bearer " + jwt.serialize()));
    }
}
