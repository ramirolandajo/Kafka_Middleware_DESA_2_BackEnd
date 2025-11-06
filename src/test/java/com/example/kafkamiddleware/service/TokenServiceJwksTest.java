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

class TokenServiceJwksTest {

    @Test
    void validateAndExtractClientId_withJwks_verifiesSignature() throws Exception {
        // generate RSA keys
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

        // create JWKSet JSON with public key
        RSAKey jwk = new RSAKey.Builder(pub).keyID("k1").build();
        JWKSet jwkSet = new JWKSet(jwk);

        File tmp = File.createTempFile("jwks", ".json");
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) {
            w.write(jwkSet.toJSONObject().toString());
        }

        // build signed JWT
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub")
                .claim("azp", "clientJ")
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
        JWSSigner signer = new RSASSASigner(priv);
        jwt.sign(signer);

        TokenService svc = new TokenService();
        // set jwksUri to file URL
        java.lang.reflect.Field f = TokenService.class.getDeclaredField("jwksUri");
        f.setAccessible(true);
        f.set(svc, tmp.toURI().toString());

        String clientId = svc.validateAndExtractClientId("Bearer " + jwt.serialize());
        assertEquals("clientJ", clientId);
    }

    @Test
    void validateAndExtractClientId_withJwks_badSignature_throws() throws Exception {
        // generate first keypair for jwt
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp1 = kpg.generateKeyPair();
        RSAPrivateKey priv1 = (RSAPrivateKey) kp1.getPrivate();

        // generate different keypair for JWKS (so signature won't match)
        KeyPair kp2 = kpg.generateKeyPair();
        RSAPublicKey pub2 = (RSAPublicKey) kp2.getPublic();

        RSAKey jwk = new RSAKey.Builder(pub2).keyID("k2").build();
        JWKSet jwkSet = new JWKSet(jwk);
        File tmp = File.createTempFile("jwks2", ".json");
        tmp.deleteOnExit();
        try (FileWriter w = new FileWriter(tmp)) { w.write(jwkSet.toJSONObject().toString()); }

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("sub")
                .claim("azp", "clientBad")
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
        JWSSigner signer = new RSASSASigner(priv1);
        jwt.sign(signer);

        TokenService svc = new TokenService();
        java.lang.reflect.Field f = TokenService.class.getDeclaredField("jwksUri");
        f.setAccessible(true);
        f.set(svc, tmp.toURI().toString());

        assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId("Bearer " + jwt.serialize()));
    }

    @Test
    void validateAndExtractClientId_missingClientId_throws() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(null)
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        JWSSigner signer = new RSASSASigner(priv);
        jwt.sign(signer);

        TokenService svc = new TokenService();
        assertThrows(TokenService.TokenValidationException.class, () -> svc.validateAndExtractClientId("Bearer " + jwt.serialize()));
    }
}
