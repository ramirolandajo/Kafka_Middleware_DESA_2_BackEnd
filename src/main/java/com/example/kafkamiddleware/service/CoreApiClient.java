package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class CoreApiClient {

    private static final Logger log = LoggerFactory.getLogger(CoreApiClient.class);

    @Value("${core.api.url:http://localhost:8082/api}")
    private String coreApiBase;

    @Value("${core.api.resource-path:/core/events}")
    private String coreApiPath;

    @Value("${app.core.forward.enabled:true}")
    private boolean forwardEnabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private final EventStore eventStore;

    public CoreApiClient(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Async
    public void forwardAsync(Event event, String canonicalOrigin) {
        try {
            forward(event, canonicalOrigin);
        } catch (Exception ex) {
            log.error("[Middleware] Error reenviando evento id={} al Core: {}", event.getId(), ex.getMessage());
        }
    }

    public void forward(Event event, String canonicalOrigin) {
        if (!forwardEnabled) {
            log.debug("[Middleware] Reenvío a Core deshabilitado (app.core.forward.enabled=false)");
            return;
        }
        String url = buildUrl();
        Map<String, Object> body = buildBody(event, canonicalOrigin);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, req, String.class);
            int status = resp.getStatusCode().value();
            if (status == 200 || status == 202) {
                log.info("[Middleware] Evento id={} reenviado a Core OK (status={})", event.getId(), status);
                eventStore.updateStatus(event.getId(), EventStatus.DELIVERED);
            } else {
                log.warn("[Middleware] Reenvío a Core devolvió status={} para id={}", status, event.getId());
            }
        } catch (Exception ex) {
            log.error("[Middleware] Falló reenvío a Core ({}): {}", url, ex.getMessage());
        }
    }

    private String buildUrl() {
        String base = coreApiBase != null ? coreApiBase.trim() : "";
        String path = coreApiPath != null ? coreApiPath.trim() : "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return base + path;
    }

    private Map<String, Object> buildBody(Event event, String canonicalOrigin) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", event.getType());
        body.put("payload", event.getPayload());
        String ts = formatTimestamp(event.getTimestamp());
        if (ts != null) body.put("timestamp", ts);
        body.put("originModule", canonicalOrigin);
        return body;
    }

    private String formatTimestamp(Instant instant) {
        if (instant == null) return null;
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        // Formato sin zona, solo fecha y hora (segundos)
        return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }
}

