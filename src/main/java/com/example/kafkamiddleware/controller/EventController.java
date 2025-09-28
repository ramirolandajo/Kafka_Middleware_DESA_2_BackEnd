package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventDto;
import com.example.kafkamiddleware.dto.EventStatus;
import com.example.kafkamiddleware.service.CoreApiClient;
import com.example.kafkamiddleware.service.EventStore;
import com.example.kafkamiddleware.service.EventValidator;
import com.example.kafkamiddleware.service.ModuleMessageStore;
import com.example.kafkamiddleware.service.ModuleRegistry;
import com.example.kafkamiddleware.service.TokenService;
import com.example.kafkamiddleware.service.TokenService.TokenValidationException;
import com.example.kafkamiddleware.service.OriginMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final TokenService tokenService;
    private final ModuleRegistry moduleRegistry;
    private final EventValidator eventValidator;
    private final EventStore eventStore;
    private final ModuleMessageStore moduleMessageStore;
    private final ObjectMapper objectMapper;
    private final CoreApiClient coreApiClient;
    private final OriginMapper originMapper;

    public EventController(TokenService tokenService,
                           ModuleRegistry moduleRegistry,
                           EventValidator eventValidator,
                           EventStore eventStore,
                           ModuleMessageStore moduleMessageStore,
                           ObjectMapper objectMapper,
                           CoreApiClient coreApiClient,
                           OriginMapper originMapper) {
        this.tokenService = tokenService;
        this.moduleRegistry = moduleRegistry;
        this.eventValidator = eventValidator;
        this.eventStore = eventStore;
        this.moduleMessageStore = moduleMessageStore;
        this.objectMapper = objectMapper;
        this.coreApiClient = coreApiClient;
        this.originMapper = originMapper;
    }

    @PostMapping
    public ResponseEntity<?> receiveEvent(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody Map<String, Object> body) {
        log.info("[Middleware] POST /events recibido. Auth header presente? {} | Claves body: {}", authorization != null && !authorization.isBlank(), body != null ? body.keySet() : "<null>");
        // Validate against JSON schema first
        var schemaErrors = eventValidator.validate(body);
        if (schemaErrors != null && !schemaErrors.isEmpty()) {
            log.warn("[Middleware] Schema validation failed: {}", schemaErrors);
            return ResponseEntity.badRequest().body(Map.of("error", "schema_validation_failed", "details", schemaErrors));
        }

        String clientId;
        try {
            clientId = tokenService.validateAndExtractClientId(authorization);
        } catch (TokenValidationException e) {
            log.warn("[Middleware] Token validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        if (!moduleRegistry.isAuthorizedModule(clientId)) {
            log.warn("[Middleware] Module not authorized: {}", clientId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Module not authorized", "clientId", clientId));
        }

        // Normalize fields
        String type = castToString(body.get("type"));
        Object payloadObj = body.get("payload");
        Map<String, Object> payload = null;
        if (payloadObj instanceof Map) {
            payload = (Map<String, Object>) payloadObj;
        } else if (payloadObj instanceof String) {
            String s = (String) payloadObj;
            try {
                // intentar parsear como JSON
                payload = objectMapper.readValue(s, Map.class);
            } catch (Exception ex) {
                // si no es JSON, guardar como string simple
                payload = Map.of("value", s);
            }
        } else if (payloadObj == null) {
            payload = Map.of();
        } else {
            // try to convert to map
            payload = objectMapper.convertValue(payloadObj, Map.class);
        }

        Instant timestamp = parseTimestamp(body.get("timestamp"));

        String originModuleRaw = castToString(body.get("originModule"));
        String originModuleFinal = clientId; // SIEMPRE usar el clientId del token
        if (originModuleRaw != null && !originModuleRaw.isBlank() && !originModuleRaw.equals(clientId)) {
            log.warn("[Middleware] originModule provisto ('{}') no coincide con token ('{}'). Se usará el del token.", originModuleRaw, clientId);
        }

        // Validate contract: type, payload, timestamp, originModule
        if (type == null || type.isBlank()) {
            log.warn("[Middleware] type missing in request");
            return ResponseEntity.badRequest().body(Map.of("error", "type is required"));
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }

        EventDto eventDto = new EventDto(type, payload, timestamp, originModuleFinal);

        // Persist as Event with status RECEIVED
        Event event = new Event(type, payload, timestamp, originModuleFinal, EventStatus.RECEIVED);
        Event saved = eventStore.save(event);
        log.info("[Middleware] Event recibido y guardado con id={} status={}", saved.getId(), EventStatus.RECEIVED);

        // Forward asynchronously to Core with canonical origin names (Ventas/Inventario/Analitica)
        String canonicalOrigin = originMapper.map(clientId);
        coreApiClient.forwardAsync(saved, canonicalOrigin);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "received", "eventId", saved.getId()));
    }

    @GetMapping
    public ResponseEntity<?> listEvents(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam(value = "status", required = false) String status) {
        String clientId;
        try {
            clientId = tokenService.validateAndExtractClientId(authorization);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        List<Event> events;
        if (status == null || status.isBlank()) {
            events = eventStore.listAll();
        } else {
            try {
                EventStatus st = EventStatus.valueOf(status.toUpperCase());
                events = eventStore.listByStatus(st);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid status"));
            }
        }
        log.info("[Middleware] GET /events listado solicitado. status={} authPresent={}", status, authorization != null && !authorization.isBlank());
        return ResponseEntity.ok(events);
    }

    @GetMapping("/poll")
    public ResponseEntity<?> pollEvents(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String clientId;
        try {
            clientId = tokenService.validateAndExtractClientId(authorization);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        if (!moduleRegistry.isAuthorizedModule(clientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Module not authorized"));
        }

        log.info("[Middleware] GET /events/poll solicitado. authPresent={}", authorization != null && !authorization.isBlank());
        var messages = moduleMessageStore.pollMessagesForModule(clientId);
        return ResponseEntity.ok(messages);
    }

    private String castToString(Object o) {
        if (o == null) return null;
        return String.valueOf(o);
    }

    private Instant parseTimestamp(Object ts) {
        if (ts == null) return null;
        if (ts instanceof Number) {
            long v = ((Number) ts).longValue();
            // assume epoch millis
            return Instant.ofEpochMilli(v);
        }
        if (ts instanceof String) {
            try {
                return Instant.parse((String) ts);
            } catch (Exception e) {
                // not ISO format
                try {
                    long v = Long.parseLong((String) ts);
                    return Instant.ofEpochMilli(v);
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        if (ts instanceof List) {
            try {
                List<?> arr = (List<?>) ts;
                if (arr.size() >= 6) {
                    int year = toInt(arr.get(0));
                    int month = toInt(arr.get(1));
                    int day = toInt(arr.get(2));
                    int hour = toInt(arr.get(3));
                    int minute = toInt(arr.get(4));
                    int second = toInt(arr.get(5));
                    int nano = 0;
                    if (arr.size() > 6) nano = toInt(arr.get(6));
                    LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second, nano);
                    return ldt.toInstant(ZoneOffset.UTC);
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(String.valueOf(o));
    }
}
