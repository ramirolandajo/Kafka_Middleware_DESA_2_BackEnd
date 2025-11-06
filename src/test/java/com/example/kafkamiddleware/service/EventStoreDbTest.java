package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import com.example.kafkamiddleware.persistence.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EventStoreDbTest {

    @Autowired
    EventRepository repository;

    @Test
    void save_and_list_by_status_using_db() {
        EventStore store = new EventStore();
        // inject repository and objectMapper
        try {
            java.lang.reflect.Field repoField = EventStore.class.getDeclaredField("repository");
            repoField.setAccessible(true);
            repoField.set(store, repository);

            java.lang.reflect.Field storageField = EventStore.class.getDeclaredField("storageType");
            storageField.setAccessible(true);
            storageField.set(store, "DB");

            java.lang.reflect.Field omField = EventStore.class.getDeclaredField("objectMapper");
            omField.setAccessible(true);
            omField.set(store, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Event ev = new Event("t-db", Map.of("x",1), Instant.now(), "moddb", EventStatus.RECEIVED);
        Event saved = store.save(ev);
        assertNotNull(saved.getId());

        assertEquals(1, store.listByStatus(EventStatus.RECEIVED).size());

        store.updateStatus(saved.getId(), EventStatus.DELIVERED);
        Event found = store.findById(saved.getId());
        assertEquals(EventStatus.DELIVERED, found.getStatus());
    }
}

