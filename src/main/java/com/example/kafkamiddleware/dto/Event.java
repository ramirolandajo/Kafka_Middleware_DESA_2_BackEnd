package com.example.kafkamiddleware.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {

    private String id;
    private String type;
    private Map<String, Object> payload;
    private Instant timestamp;
    private String originModule;
    private EventStatus status;

    public Event() {
        this.id = UUID.randomUUID().toString();
    }

    public Event(String type, Map<String, Object> payload, Instant timestamp, String originModule, EventStatus status) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
        this.originModule = originModule;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getOriginModule() {
        return originModule;
    }

    public void setOriginModule(String originModule) {
        this.originModule = originModule;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(type, event.type) && Objects.equals(payload, event.payload) && Objects.equals(timestamp, event.timestamp) && Objects.equals(originModule, event.originModule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, payload, timestamp, originModule);
    }
}

