package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventStoreMemoryDedupeTest {

    @Test
    void save_duplicateEvent_returnsExistingInstance() {
        EventStore store = new EventStore();
        try {
            java.lang.reflect.Field f = EventStore.class.getDeclaredField("storageType");
            f.setAccessible(true);
            f.set(store, "MEMORY");
        } catch (Exception e) { throw new RuntimeException(e); }

        Instant ts = Instant.now();
        Event a = new Event("tp", Map.of("k",1), ts, "m1", EventStatus.RECEIVED);
        Event b = new Event("tp", Map.of("k",1), ts, "m1", EventStatus.RECEIVED);

        Event saved1 = store.save(a);
        Event saved2 = store.save(b);

        assertSame(saved1, saved2);
    }
}

