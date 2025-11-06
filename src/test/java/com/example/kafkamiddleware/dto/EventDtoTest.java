package com.example.kafkamiddleware.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventDtoTest {

    @Test
    void noArgsConstructor_createsEmptyObject() {
        EventDto dto = new EventDto();
        assertNull(dto.getType());
        assertNull(dto.getPayload());
        assertNull(dto.getTimestamp());
        assertNull(dto.getOriginModule());
    }

    @Test
    void allArgsConstructor_and_getters_workCorrectly() {
        Instant ts = Instant.now();
        Map<String, Object> payload = Map.of("key", "value");
        EventDto dto = new EventDto("event-type", payload, ts, "event-origin");

        assertEquals("event-type", dto.getType());
        assertEquals(payload, dto.getPayload());
        assertEquals(ts, dto.getTimestamp());
        assertEquals("event-origin", dto.getOriginModule());
    }

    @Test
    void setters_workCorrectly() {
        EventDto dto = new EventDto();
        Instant ts = Instant.now();
        Map<String, Object> payload = Map.of("key", "value");

        dto.setType("new-type");
        dto.setPayload(payload);
        dto.setTimestamp(ts);
        dto.setOriginModule("new-origin");

        assertEquals("new-type", dto.getType());
        assertEquals(payload, dto.getPayload());
        assertEquals(ts, dto.getTimestamp());
        assertEquals("new-origin", dto.getOriginModule());
    }
}
