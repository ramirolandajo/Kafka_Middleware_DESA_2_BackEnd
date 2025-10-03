package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.persistence.EventAckEntity;
import com.example.kafkamiddleware.persistence.EventAckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AckService {

    private static final Logger log = LoggerFactory.getLogger(AckService.class);

    private final EventAckRepository ackRepository;
    private final EventStore eventStore;

    public static class AckResult {
        public final EventAckEntity entity;
        public final boolean created;
        public AckResult(EventAckEntity entity, boolean created) {
            this.entity = entity;
            this.created = created;
        }
    }

    public AckService(EventAckRepository ackRepository, EventStore eventStore) {
        this.ackRepository = ackRepository;
        this.eventStore = eventStore;
    }

    @Transactional
    public AckResult ack(String eventId, String consumerCanonical, Instant consumedAt) {
        if (consumedAt == null) consumedAt = Instant.now();

        var existing = ackRepository.findByEventIdAndConsumer(eventId, consumerCanonical);
        if (existing.isPresent()) {
            var ent = existing.get();
            ent.setLastSeenAt(consumedAt);
            ent.setAttempts(ent.getAttempts() + 1);
            ackRepository.save(ent);
            log.info("[ACK] consumer={} eventId={} status={} attempts={} (idempotent)", consumerCanonical, eventId, ent.getStatus(), ent.getAttempts());
            return new AckResult(ent, false);
        }

        // Log de existencia del evento (opcional, no bloquea)
        Event ev = eventStore.findById(eventId);
        if (ev == null) {
            log.warn("[ACK] eventId={} no encontrado en store, se registra ACK igualmente.", eventId);
        }

        var ent = new EventAckEntity();
        ent.setEventId(eventId);
        ent.setConsumer(consumerCanonical);
        ent.setStatus("CONSUMED");
        ent.setFirstSeenAt(consumedAt);
        ent.setLastSeenAt(consumedAt);
        ent.setAttempts(1);
        try {
            var saved = ackRepository.save(ent);
            log.info("[ACK] consumer={} eventId={} status={} attempts=1 (created)", consumerCanonical, eventId, saved.getStatus());
            return new AckResult(saved, true);
        } catch (DataIntegrityViolationException dup) {
            // Otra transacción insertó primero: leer y actuar idempotente
            var ent2 = ackRepository.findByEventIdAndConsumer(eventId, consumerCanonical).orElseThrow();
            ent2.setLastSeenAt(consumedAt);
            ent2.setAttempts(ent2.getAttempts() + 1);
            ackRepository.save(ent2);
            log.info("[ACK] consumer={} eventId={} status={} attempts={} (concurrent)", consumerCanonical, eventId, ent2.getStatus(), ent2.getAttempts());
            return new AckResult(ent2, false);
        }
    }
}
