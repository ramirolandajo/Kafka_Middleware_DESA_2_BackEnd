package com.example.kafkamiddleware.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EventRepositoryTest {

    @Autowired
    private EventRepository repository;

    private EventEntity createAndSaveEvent(String status, String signature) {
        EventEntity entity = new EventEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setType("test-type");
        entity.setPayloadJson("{}");
        entity.setTimestamp(Instant.now());
        entity.setOriginModule("test-module");
        entity.setStatus(status);
        entity.setSignature(signature);
        return repository.save(entity);
    }

    @Test
    void findBySignature_whenExists_returnsEntity() {
        String signature = "sig-123";
        createAndSaveEvent("RECEIVED", signature);

        Optional<EventEntity> found = repository.findBySignature(signature);

        assertTrue(found.isPresent());
        assertEquals(signature, found.get().getSignature());
    }

    @Test
    void findBySignature_whenNotExists_returnsEmpty() {
        Optional<EventEntity> found = repository.findBySignature("non-existent-sig");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByStatus_returnsCorrectEntities() {
        createAndSaveEvent("RECEIVED", "sig-1");
        createAndSaveEvent("DELIVERED", "sig-2");
        createAndSaveEvent("DELIVERED", "sig-3");

        List<EventEntity> received = repository.findByStatus("RECEIVED");
        assertEquals(1, received.size());

        List<EventEntity> delivered = repository.findByStatus("DELIVERED");
        assertEquals(2, delivered.size());
    }
}
