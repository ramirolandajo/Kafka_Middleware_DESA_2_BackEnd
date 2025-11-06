package com.example.kafkamiddleware.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventAckEntityTest {

    @Test
    void gettersAndSetters_workCorrectly() {
        EventAckEntity entity = new EventAckEntity();
        Long id = 1L;
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        entity.setId(id);
        entity.setEventId(eventId);
        entity.setConsumer("test-consumer");
        entity.setStatus("CONSUMED");
        entity.setFirstSeenAt(now);
        entity.setLastSeenAt(now);
        entity.setAttempts(1);

        assertEquals(id, entity.getId());
        assertEquals(eventId, entity.getEventId());
        assertEquals("test-consumer", entity.getConsumer());
        assertEquals("CONSUMED", entity.getStatus());
        assertEquals(now, entity.getFirstSeenAt());
        assertEquals(now, entity.getLastSeenAt());
        assertEquals(1, entity.getAttempts());
    }
}
