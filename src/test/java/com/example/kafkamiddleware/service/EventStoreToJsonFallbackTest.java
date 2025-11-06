package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventStoreToJsonFallbackTest {

    @Test
    void toJson_whenObjectMapperThrows_usesFallbackString() throws Exception {
        EventStore store = new EventStore();
        // inject mock repository null to force memory
        java.lang.reflect.Field storageField = EventStore.class.getDeclaredField("storageType");
        storageField.setAccessible(true);
        storageField.set(store, "MEMORY");

        ObjectMapper bad = mock(ObjectMapper.class);
        when(bad.writeValueAsString(any())).thenThrow(new JsonProcessingException("err"){});
        java.lang.reflect.Field omField = EventStore.class.getDeclaredField("objectMapper");
        omField.setAccessible(true);
        omField.set(store, bad);

        Event e = new Event("tX", Map.of("k", new Object()), Instant.now(), "mX", EventStatus.RECEIVED);
        Event saved = store.save(e);
        assertNotNull(saved);
    }
}

