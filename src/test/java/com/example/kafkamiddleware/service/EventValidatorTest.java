package com.example.kafkamiddleware.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventValidatorTest {

    private EventValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new EventValidator(new ObjectMapper());
        // Init loads schema from classpath resource
        validator.init();
    }

    @Test
    void validate_validPayload_returnsEmptySet() {
        Map<String, Object> payload = Map.of("type", "my-event", "payload", Map.of("a", 1));
        Set<String> errors = validator.validate(payload);
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_missingType_returnsErrors() {
        Map<String, Object> payload = Map.of("payload", Map.of("a", 1));
        Set<String> errors = validator.validate(payload);
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    void validate_nullPayload_returnsNoErrors() {
        Map<String, Object> event = Map.of("type", "my-event"); // Payload is missing, not null
        Set<String> errors = validator.validate(event);
        assertTrue(errors.isEmpty(), "Validation should pass when payload is missing but not required");
    }

    @Test
    void validate_payloadAsString_returnsNoErrors() {
        Map<String, Object> event = Map.of("type", "my-event", "payload", "not-an-object");
        Set<String> errors = validator.validate(event);
        assertTrue(errors.isEmpty(), "Validation should pass when payload is a string as allowed by schema");
    }
}
