package com.example.kafkamiddleware.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventValidatorTimestampTest {

    private EventValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new EventValidator(new ObjectMapper());
        validator.init();
    }

    @Test
    void validate_timestampAsEpochMillis_valid() {
        Map<String, Object> payload = Map.of("type", "evt", "timestamp", java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString());
        Set<String> errors = validator.validate(payload);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validate_timestampAsIsoString_valid() {
        Map<String, Object> payload = Map.of("type", "evt", "timestamp", "2020-01-01T00:00:00Z");
        Set<String> errors = validator.validate(payload);
        assertTrue(errors.isEmpty());
    }
}

