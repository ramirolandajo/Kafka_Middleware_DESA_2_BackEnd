package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.persistence.EventAckEntity;
import com.example.kafkamiddleware.persistence.EventAckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AckServiceTest {

    private EventAckRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(EventAckRepository.class);
    }

    @Test
    void ack_whenExisting_ackShouldBeUpdatedAndCreatedFalse() {
        EventStore eventStore = mock(EventStore.class);
        AckService ackService = new AckService(repo, eventStore);

        String eventId = "evt-1";
        String consumer = "Ventas";
        Instant now = Instant.now();
        EventAckEntity existing = new EventAckEntity();
        existing.setEventId(eventId);
        existing.setConsumer(consumer);
        existing.setStatus("CONSUMED");
        existing.setFirstSeenAt(now.minusSeconds(60));
        existing.setLastSeenAt(now.minusSeconds(60));
        existing.setAttempts(1);

        when(repo.findByEventIdAndConsumer(eventId, consumer)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AckService.AckResult res = ackService.ack(eventId, consumer, now);

        assertNotNull(res);
        assertFalse(res.created);
        assertEquals(existing, res.entity);
        assertEquals(2, existing.getAttempts());
        assertEquals(now, existing.getLastSeenAt());
        verify(repo, times(1)).save(existing);
    }

    @Test
    void ack_whenNotExisting_shouldCreateEntryAndReturnCreatedTrue() {
        EventStore eventStore = mock(EventStore.class);
        AckService ackService = new AckService(repo, eventStore);

        String eventId = "evt-2";
        String consumer = "Inventario";
        Instant now = Instant.now();

        when(repo.findByEventIdAndConsumer(eventId, consumer)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> {
            EventAckEntity ent = invocation.getArgument(0);
            ent.setId(100L);
            return ent;
        });

        AckService.AckResult res = ackService.ack(eventId, consumer, now);
        assertNotNull(res);
        assertTrue(res.created);
        assertNotNull(res.entity.getId());
        assertEquals(1, res.entity.getAttempts());
        assertEquals("CONSUMED", res.entity.getStatus());
        verify(repo, times(1)).save(any(EventAckEntity.class));
    }

    @Test
    void ack_whenSaveThrowsDuplicate_shouldHandleConcurrencyAndReturnCreatedFalse() {
        EventStore eventStore = mock(EventStore.class);
        AckService ackService = new AckService(repo, eventStore);

        String eventId = "evt-3";
        String consumer = "Analitica";
        Instant now = Instant.now();

        EventAckEntity concurrent = new EventAckEntity();
        concurrent.setEventId(eventId);
        concurrent.setConsumer(consumer);
        concurrent.setStatus("CONSUMED");
        concurrent.setFirstSeenAt(now.minusSeconds(10));
        concurrent.setLastSeenAt(now.minusSeconds(10));
        concurrent.setAttempts(1);

// findBy: primero vacío (intento de creación), luego devuelve la entidad concurrente
        when(repo.findByEventIdAndConsumer(eventId, consumer))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));

// save: primero lanza DataIntegrityViolationException (concurrencia), luego devuelve la entidad
        when(repo.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AckService.AckResult res = ackService.ack(eventId, consumer, now);
        assertNotNull(res);
        assertFalse(res.created);
        assertEquals(2, res.entity.getAttempts());
        assertEquals(now, res.entity.getLastSeenAt());
        verify(repo, atLeast(1)).save(any(EventAckEntity.class));
    }

    @Test
    void ack_whenConsumedAtIsNull_usesCurrentTime() {
        EventStore eventStore = mock(EventStore.class);
        AckService ackService = new AckService(repo, eventStore);

        String eventId = "evt-4";
        String consumer = "Ventas";

        when(repo.findByEventIdAndConsumer(eventId, consumer)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> {
            EventAckEntity ent = invocation.getArgument(0);
            ent.setId(101L);
            return ent;
        });

        Instant before = Instant.now();
        AckService.AckResult res = ackService.ack(eventId, consumer, null);
        Instant after = Instant.now();

        assertNotNull(res);
        assertTrue(res.created);
        assertNotNull(res.entity.getFirstSeenAt());
        assertTrue(res.entity.getFirstSeenAt().isAfter(before.minusMillis(1)) && res.entity.getFirstSeenAt().isBefore(after.plusMillis(1)));
        verify(repo).save(any(EventAckEntity.class));
    }

    @Test
    void ack_whenEventNotFoundInStore_stillCreatesAck() {
        EventStore eventStore = mock(EventStore.class);
        AckService ackService = new AckService(repo, eventStore);

        String eventId = "evt-5";
        String consumer = "Inventario";
        Instant now = Instant.now();

        when(eventStore.findById(eventId)).thenReturn(null); // Event not found
        when(repo.findByEventIdAndConsumer(eventId, consumer)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> {
            EventAckEntity ent = invocation.getArgument(0);
            ent.setId(102L);
            return ent;
        });

        AckService.AckResult res = ackService.ack(eventId, consumer, now);

        assertNotNull(res);
        assertTrue(res.created);
        assertEquals(eventId, res.entity.getEventId());
        
        // Verify that we tried to find the event, but still created the ack
        verify(eventStore, times(1)).findById(eventId);
        verify(repo, times(1)).save(any(EventAckEntity.class));
    }
}
