package com.example.kafkamiddleware.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void equals_and_hashCode_basedOnFields() {
        Instant ts = Instant.now();
        Event a = new Event("type", Map.of("k",1), ts, "mod", EventStatus.RECEIVED);
        Event b = new Event("type", Map.of("k",1), ts, "mod", EventStatus.RECEIVED);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setOriginModule("other");
        assertNotEquals(a, b);
    }

    @Test
    void noArgsConstructor_initializesId() {
        Event event = new Event();
        assertNotNull(event.getId());
        // Check if it's a valid UUID
        assertDoesNotThrow(() -> UUID.fromString(event.getId()));
    }

    @Test
    void gettersAndSetters_workCorrectly() {
        Event event = new Event();
        String id = UUID.randomUUID().toString();
        Instant ts = Instant.now();
        Map<String, Object> payload = Map.of("test", "value");

        event.setId(id);
        event.setType("test-type");
        event.setPayload(payload);
        event.setTimestamp(ts);
        event.setOriginModule("test-module");
        event.setStatus(EventStatus.DELIVERED);

        assertEquals(id, event.getId());
        assertEquals("test-type", event.getType());
        assertEquals(payload, event.getPayload());
        assertEquals(ts, event.getTimestamp());
        assertEquals("test-module", event.getOriginModule());
        assertEquals(EventStatus.DELIVERED, event.getStatus());
    }
}
