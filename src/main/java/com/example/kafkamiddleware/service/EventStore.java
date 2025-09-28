package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import com.example.kafkamiddleware.persistence.EventEntity;
import com.example.kafkamiddleware.persistence.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

@Service
public class EventStore {

    private static final Logger log = LoggerFactory.getLogger(EventStore.class);

    @Value("${app.storage.type:MEMORY}")
    private String storageType;

    @Autowired(required = false)
    private EventRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, Event> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void onInit() {
        log.info("[Middleware] EventStore inicializado. storageType={} | repoDisponible={}", storageType, repository != null);
    }

    @Transactional
    public Event save(Event e) {
        if (isDb()) {
            log.debug("[Middleware] Guardando evento en H2/JPA (DATABASE)");
            String payloadJson = toJson(e.getPayload());
            String signature = signatureOf(e.getType(), payloadJson, e.getTimestamp().toEpochMilli(), e.getOriginModule());

            Optional<EventEntity> existing = repository.findBySignature(signature);
            if (existing.isPresent()) {
                return toDto(existing.get());
            }

            EventEntity entity = new EventEntity();
            entity.setId(e.getId());
            entity.setType(e.getType());
            entity.setPayloadJson(payloadJson);
            entity.setTimestamp(e.getTimestamp());
            entity.setOriginModule(e.getOriginModule());
            entity.setStatus(e.getStatus().name());
            entity.setSignature(signature);

            EventEntity saved = repository.save(entity);
            // asegurar flush para que sea visible inmediatamente desde otras conexiones
            repository.flush();
            return toDto(saved);
        }

        log.debug("[Middleware] Guardando evento en memoria (MEMORY)");
        // In-memory fallback (dedupe por equals)
        for (Event existing : store.values()) {
            if (existing.equals(e)) {
                return existing;
            }
        }
        store.put(e.getId(), e);
        return e;
    }

    @Transactional(readOnly = true)
    public List<Event> listAll() {
        if (isDb()) {
            return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
        }
        return new ArrayList<>(store.values());
    }

    @Transactional(readOnly = true)
    public List<Event> listByStatus(EventStatus status) {
        if (isDb()) {
            return repository.findByStatus(status.name()).stream().map(this::toDto).collect(Collectors.toList());
        }
        return store.values().stream().filter(ev -> ev.getStatus() == status).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Event findById(String id) {
        if (isDb()) {
            return repository.findById(id).map(this::toDto).orElse(null);
        }
        return store.get(id);
    }

    @Transactional
    public void updateStatus(String id, EventStatus status) {
        if (isDb()) {
            repository.findById(id).ifPresent(ent -> {
                ent.setStatus(status.name());
                repository.save(ent);
            });
            return;
        }
        Event e = store.get(id);
        if (e != null) {
            e.setStatus(status);
        }
    }

    private boolean isDb() {
        // Si se fuerza explícitamente MEMORY, usar memoria
        if ("MEMORY".equalsIgnoreCase(storageType)) return false;
        // Caso contrario, si hay repositorio disponible, usar DB (H2/JPA)
        return repository != null;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            // fallback mínimo
            return String.valueOf(map);
        }
    }

    private Event toDto(EventEntity ent) {
        Event dto = new Event();
        dto.setId(ent.getId());
        dto.setType(ent.getType());
        try {
            Map<String, Object> payload = objectMapper.readValue(ent.getPayloadJson(), Map.class);
            dto.setPayload(payload);
        } catch (Exception ex) {
            dto.setPayload(Map.of("raw", ent.getPayloadJson()));
        }
        dto.setTimestamp(ent.getTimestamp());
        dto.setOriginModule(ent.getOriginModule());
        dto.setStatus(EventStatus.valueOf(ent.getStatus()));
        return dto;
    }

    private String signatureOf(String type, String payloadJson, long tsMillis, String origin) {
        String base = type + "|" + payloadJson + "|" + tsMillis + "|" + origin;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(base.hashCode());
        }
    }
}
