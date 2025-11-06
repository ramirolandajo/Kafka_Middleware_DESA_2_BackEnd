package com.example.kafkamiddleware.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EventAckRepositoryTest {

    @Autowired
    private EventAckRepository repository;

    private EventAckEntity createAndSaveAck(String eventId, String consumer) {
        EventAckEntity entity = new EventAckEntity();
        entity.setEventId(eventId);
        entity.setConsumer(consumer);
        entity.setStatus("CONSUMED");
        entity.setFirstSeenAt(Instant.now());
        entity.setLastSeenAt(Instant.now());
        entity.setAttempts(1);
        return repository.save(entity);
    }

    @Test
    void findByEventIdAndConsumer_whenExists_returnsEntity() {
        String eventId = UUID.randomUUID().toString();
        String consumer = "test-consumer";
        createAndSaveAck(eventId, consumer);

        Optional<EventAckEntity> found = repository.findByEventIdAndConsumer(eventId, consumer);

        assertTrue(found.isPresent());
        assertEquals(eventId, found.get().getEventId());
        assertEquals(consumer, found.get().getConsumer());
    }

    @Test
    void findByEventIdAndConsumer_whenNotExists_returnsEmpty() {
        Optional<EventAckEntity> found = repository.findByEventIdAndConsumer("non-existent-event", "non-existent-consumer");
        assertTrue(found.isEmpty());
    }
}
