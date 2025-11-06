package com.example.kafkamiddleware.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventEntityTest {

    @Test
    void gettersAndSetters_workCorrectly() {
        EventEntity entity = new EventEntity();
        String id = UUID.randomUUID().toString();
        Instant ts = Instant.now();

        entity.setId(id);
        entity.setType("event-type");
        entity.setPayloadJson("{'key':'value'}");
        entity.setTimestamp(ts);
        entity.setOriginModule("origin");
        entity.setStatus("RECEIVED");
        entity.setSignature("abc-123");

        assertEquals(id, entity.getId());
        assertEquals("event-type", entity.getType());
        assertEquals("{'key':'value'}", entity.getPayloadJson());
        assertEquals(ts, entity.getTimestamp());
        assertEquals("origin", entity.getOriginModule());
        assertEquals("RECEIVED", entity.getStatus());
        assertEquals("abc-123", entity.getSignature());
    }
}
