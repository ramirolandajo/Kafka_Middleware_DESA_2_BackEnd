package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import com.example.kafkamiddleware.persistence.EventEntity;
import com.example.kafkamiddleware.persistence.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventStoreTest {

    private EventRepository repository;
    private ObjectMapper objectMapper;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        repository = mock(EventRepository.class);
        objectMapper = new ObjectMapper();
        eventStore = new EventStore();
        // Inject mocks using reflection
        try {
            java.lang.reflect.Field repoField = EventStore.class.getDeclaredField("repository");
            repoField.setAccessible(true);
            repoField.set(eventStore, repository);

            java.lang.reflect.Field mapperField = EventStore.class.getDeclaredField("objectMapper");
            mapperField.setAccessible(true);
            mapperField.set(eventStore, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setStorageMode(String mode) {
        try {
            java.lang.reflect.Field storageTypeField = EventStore.class.getDeclaredField("storageType");
            storageTypeField.setAccessible(true);
            storageTypeField.set(eventStore, mode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Helper to create a fully populated EventEntity
    private EventEntity createTestEventEntity(String id, String type, String payloadJson, Instant timestamp, String originModule, String status, String signature) {
        EventEntity entity = new EventEntity();
        entity.setId(id);
        entity.setType(type);
        entity.setPayloadJson(payloadJson);
        entity.setTimestamp(timestamp);
        entity.setOriginModule(originModule);
        entity.setStatus(status);
        entity.setSignature(signature);
        return entity;
    }

    @Test
    void saveAndFindById_memoryMode() {
        setStorageMode("MEMORY");
        Event event = new Event("t1", Map.of("a", 1), Instant.now(), "mod1", EventStatus.RECEIVED);
        eventStore.save(event);

        Event found = eventStore.findById(event.getId());
        assertNotNull(found);
        assertEquals(event.getId(), found.getId());
    }

    @Test
    void listAll_memoryMode() {
        setStorageMode("MEMORY");
        eventStore.save(new Event("t1", Map.of(), Instant.now(), "m1", EventStatus.RECEIVED));
        eventStore.save(new Event("t2", Map.of(), Instant.now(), "m2", EventStatus.DELIVERED));

        List<Event> all = eventStore.listAll();
        assertEquals(2, all.size());
    }

    @Test
    void listByStatus_memoryMode() {
        setStorageMode("MEMORY");
        eventStore.save(new Event("t1", Map.of(), Instant.now(), "m1", EventStatus.RECEIVED));
        eventStore.save(new Event("t2", Map.of(), Instant.now(), "m2", EventStatus.DELIVERED));

        List<Event> delivered = eventStore.listByStatus(EventStatus.DELIVERED);
        assertEquals(1, delivered.size());
        assertEquals("t2", delivered.get(0).getType());
    }

    @Test
    void updateStatus_memoryMode() {
        setStorageMode("MEMORY");
        Event event = new Event("t1", Map.of(), Instant.now(), "m1", EventStatus.RECEIVED);
        eventStore.save(event);

        eventStore.updateStatus(event.getId(), EventStatus.DELIVERED);

        Event updated = eventStore.findById(event.getId());
        assertEquals(EventStatus.DELIVERED, updated.getStatus());
    }

    @Test
    void saveAndFindById_dbMode() {
        setStorageMode("DATABASE");
        Event event = new Event("t_db", Map.of("x", 1), Instant.now(), "mod_db", EventStatus.RECEIVED);
        EventEntity entity = createTestEventEntity(event.getId(), event.getType(), "{\"x\":1}", event.getTimestamp(), event.getOriginModule(), event.getStatus().name(), "signature-1");

        when(repository.findBySignature(any())).thenReturn(Optional.empty());
        when(repository.save(any(EventEntity.class))).thenReturn(entity);
        when(repository.findById(event.getId())).thenReturn(Optional.of(entity));

        eventStore.save(event);
        Event found = eventStore.findById(event.getId());

        assertNotNull(found);
        assertEquals(event.getId(), found.getId());
        verify(repository).save(any(EventEntity.class));
    }

    @Test
    void dedupeBySignature_dbMode() {
        setStorageMode("DATABASE");
        Event event = new Event("t_dedupe", Map.of(), Instant.now(), "mod_dedupe", EventStatus.RECEIVED);
        EventEntity existingEntity = createTestEventEntity("existing-id", "t_dedupe", "{}", Instant.now(), "mod_dedupe", EventStatus.RECEIVED.name(), "signature-dedupe");

        when(repository.findBySignature(any())).thenReturn(Optional.of(existingEntity));

        Event result = eventStore.save(event);

        assertEquals("existing-id", result.getId());
        verify(repository, never()).save(any());
    }

    @Test
    void listByStatus_dbMode() {
        setStorageMode("DATABASE");
        EventEntity entity = createTestEventEntity(UUID.randomUUID().toString(), "t_status", "{}", Instant.now(), "mod_status", EventStatus.DELIVERED.name(), "signature-status");
        when(repository.findAll()).thenReturn(List.of(entity)); // Mock findAll for listAll
        when(repository.findByStatus(EventStatus.DELIVERED.name())).thenReturn(List.of(entity));

        List<Event> events = eventStore.listByStatus(EventStatus.DELIVERED);

        assertEquals(1, events.size());
        verify(repository).findByStatus(EventStatus.DELIVERED.name());
    }

    @Test
    void updateStatus_dbMode() {
        setStorageMode("DATABASE");
        EventEntity entity = createTestEventEntity("event-id", "t_update", "{}", Instant.now(), "mod_update", EventStatus.RECEIVED.name(), "signature-update");
        when(repository.findById("event-id")).thenReturn(Optional.of(entity));
        when(repository.save(any(EventEntity.class))).thenReturn(entity); // Mock save to return the updated entity

        eventStore.updateStatus("event-id", EventStatus.DELIVERED);

        verify(repository).save(entity);
        assertEquals(EventStatus.DELIVERED.name(), entity.getStatus());
    }

    @Test
    void toDto_handlesInvalidJson() throws Exception {
        EventEntity entity = createTestEventEntity(UUID.randomUUID().toString(), "t_invalid_json", "not-a-json", Instant.now(), "mod_invalid", EventStatus.RECEIVED.name(), "signature-invalid");

        java.lang.reflect.Method toDto = EventStore.class.getDeclaredMethod("toDto", EventEntity.class);
        toDto.setAccessible(true);
        Event event = (Event) toDto.invoke(eventStore, entity);

        assertNotNull(event.getPayload());
        assertEquals("not-a-json", event.getPayload().get("raw"));
    }
}
