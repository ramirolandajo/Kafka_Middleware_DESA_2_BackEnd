package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventStoreMemoryTest {

    private EventStore store;

    @BeforeEach
    void setUp() {
        store = new EventStore();
        // Force MEMORY storage
        try {
            java.lang.reflect.Field f = EventStore.class.getDeclaredField("storageType");
            f.setAccessible(true);
            f.set(store, "MEMORY");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void save_and_listAndFindById_shouldWork() {
        Event e = new Event("t1", Map.of("k", "v"), Instant.now(), "mod1", EventStatus.RECEIVED);
        Event saved = store.save(e);
        assertNotNull(saved.getId());

        List<Event> all = store.listAll();
        assertTrue(all.stream().anyMatch(ev -> ev.getId().equals(saved.getId())));

        Event found = store.findById(saved.getId());
        assertNotNull(found);
        assertEquals(saved.getId(), found.getId());
    }

    @Test
    void updateStatus_shouldChangeStatus() {
        Event e = new Event("t1", Map.of("k", "v"), Instant.now(), "mod1", EventStatus.RECEIVED);
        Event saved = store.save(e);
        store.updateStatus(saved.getId(), EventStatus.DELIVERED);
        Event found = store.findById(saved.getId());
        assertEquals(EventStatus.DELIVERED, found.getStatus());
    }
}
