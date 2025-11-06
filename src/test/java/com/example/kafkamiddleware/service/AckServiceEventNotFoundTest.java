package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.persistence.EventAckEntity;
import com.example.kafkamiddleware.persistence.EventAckRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AckServiceEventNotFoundTest {

    @Test
    void ack_whenEventNotFound_logsAndCreates() {
        EventAckRepository repo = mock(EventAckRepository.class);
        EventStore store = mock(EventStore.class);
        AckService svc = new AckService(repo, store);

        String eventId = "no-such";
        String consumer = "Ventas";
        Instant now = Instant.now();

        when(repo.findByEventIdAndConsumer(eventId, consumer)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> {
            EventAckEntity e = invocation.getArgument(0);
            e.setId(999L);
            return e;
        });
        when(store.findById(eventId)).thenReturn(null);

        AckService.AckResult res = svc.ack(eventId, consumer, now);
        assertTrue(res.created);
        assertNotNull(res.entity.getId());
        verify(store).findById(eventId);
    }
}

