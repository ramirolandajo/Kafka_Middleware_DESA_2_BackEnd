package com.example.kafkamiddleware.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class TokenService {

    @Value("${security.jwks-uri:}")
    private String jwksUri;

    // simple cache for JWKSet
    private volatile JWKSet cachedJwkSet;
    private volatile long jwkSetFetchedAt = 0L; // epoch millis
    private final long jwkCacheTtl = 10 * 60 * 1000L; // 10 minutes

    public String validateAndExtractClientId(String bearerToken) throws TokenValidationException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new TokenValidationException("Authorization token missing");
        }

        String token = bearerToken;
        if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7);
        }

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new TokenValidationException("Malformed token", e);
        }

        // validate expiry
        try {
            var claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null) {
                throw new TokenValidationException("Token has no expiration");
            }
            if (exp.before(new Date())) {
                throw new TokenValidationException("Token expired");
            }
        } catch (ParseException e) {
            throw new TokenValidationException("Failed to read claims", e);
        }

        // If JWKS configured, verify signature
        if (jwksUri != null && !jwksUri.isBlank()) {
            try {
                verifySignatureWithJwks(jwt);
            } catch (Exception e) {
                throw new TokenValidationException("Token signature validation failed: " + e.getMessage(), e);
            }
        }

        try {
            var claims = jwt.getJWTClaimsSet();
            String clientId = null;
            clientId = claims.getStringClaim("azp");
            if (clientId == null) clientId = claims.getStringClaim("client_id");
            if (clientId == null) clientId = claims.getStringClaim("clientId");
            if (clientId == null) clientId = claims.getSubject();
            if (clientId == null) throw new TokenValidationException("Unable to extract client id from token");
            return clientId;
        } catch (ParseException e) {
            throw new TokenValidationException("Failed to extract claims", e);
        }
    }

    private void verifySignatureWithJwks(SignedJWT jwt) throws Exception {
        JWSHeader header = jwt.getHeader();
        String kid = header.getKeyID();
        JWKSet jwkSet = getJwkSet();
        List<JWK> matches;
        if (kid != null) {
            matches = jwkSet.getKeys().stream().filter(j -> Objects.equals(j.getKeyID(), kid)).toList();
        } else {
            matches = jwkSet.getKeys();
        }

        if (matches.isEmpty()) {
            throw new TokenValidationException("No matching JWK found for kid: " + kid);
        }

        boolean verified = false;
        for (JWK jwk : matches) {
            if (jwk instanceof RSAKey) {
                RSAKey rsa = (RSAKey) jwk;
                try {
                    JWSVerifier verifier = new RSASSAVerifier(rsa.toRSAPublicKey());
                    if (jwt.verify(verifier)) {
                        verified = true;
                        break;
                    }
                } catch (JOSEException e) {
                    // try next key
                }
            }
        }

        if (!verified) throw new TokenValidationException("Signature verification failed");
    }

    private synchronized JWKSet getJwkSet() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedJwkSet == null || (now - jwkSetFetchedAt) > jwkCacheTtl) {
            if (jwksUri == null || jwksUri.isBlank()) {
                throw new TokenValidationException("JWKS URI not configured");
            }
            try (InputStream is = new URL(jwksUri).openStream()) {
                cachedJwkSet = JWKSet.load(is);
                jwkSetFetchedAt = now;
            }
        }
        return cachedJwkSet;
    }

    public static class TokenValidationException extends Exception {
        public TokenValidationException(String message) {
            super(message);
        }

        public TokenValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
