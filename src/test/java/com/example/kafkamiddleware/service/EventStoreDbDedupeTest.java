package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import com.example.kafkamiddleware.persistence.EventEntity;
import com.example.kafkamiddleware.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EventStoreDbDedupeTest {

    @Autowired
    EventRepository repository;

    private ObjectMapper newMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    @Test
    void save_duplicateInDb_returnsExisting() {
        EventStore store = new EventStore();
        // inject repository and mapper and force DB
        try {
            java.lang.reflect.Field repoField = EventStore.class.getDeclaredField("repository");
            repoField.setAccessible(true);
            repoField.set(store, repository);

            java.lang.reflect.Field storageField = EventStore.class.getDeclaredField("storageType");
            storageField.setAccessible(true);
            storageField.set(store, "DB");

            java.lang.reflect.Field omField = EventStore.class.getDeclaredField("objectMapper");
            omField.setAccessible(true);
            omField.set(store, newMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Instant ts = Instant.now();
        Event e1 = new Event("t", Map.of("x",1), ts, "modX", EventStatus.RECEIVED);
        Event saved1 = store.save(e1);
        assertNotNull(saved1.getId());

        // create another Event with same content but different id
        Event e2 = new Event("t", Map.of("x",1), ts, "modX", EventStatus.RECEIVED);
        e2.setId("another-id");
        Event saved2 = store.save(e2);

        // should return existing entity (same id as first saved)
        assertEquals(saved1.getId(), saved2.getId());
    }

    @Test
    void toDto_whenPayloadNotJson_returnsRawInPayload() {
        // insert an EventEntity directly with invalid JSON payload
        EventEntity ent = new EventEntity();
        ent.setId("e-db-raw");
        ent.setType("t");
        ent.setPayloadJson("not-a-json");
        ent.setTimestamp(Instant.now());
        ent.setOriginModule("modX");
        ent.setStatus(EventStatus.RECEIVED.name());
        ent.setSignature("sig-raw");
        repository.save(ent);

        EventStore store = new EventStore();
        try {
            java.lang.reflect.Field repoField = EventStore.class.getDeclaredField("repository");
            repoField.setAccessible(true);
            repoField.set(store, repository);

            java.lang.reflect.Field storageField = EventStore.class.getDeclaredField("storageType");
            storageField.setAccessible(true);
            storageField.set(store, "DB");

            java.lang.reflect.Field omField = EventStore.class.getDeclaredField("objectMapper");
            omField.setAccessible(true);
            omField.set(store, newMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Event found = store.findById("e-db-raw");
        assertNotNull(found);
        assertTrue(found.getPayload().containsKey("raw"));
        assertEquals("not-a-json", found.getPayload().get("raw"));
    }
}
