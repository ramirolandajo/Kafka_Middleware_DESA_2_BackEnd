package com.example.kafkamiddleware.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventDto {

    private String type;
    private Map<String, Object> payload;
    private Instant timestamp;

    @JsonProperty("originModule")
    private String originModule;

    public EventDto() {
    }

    public EventDto(String type, Map<String, Object> payload, Instant timestamp, String originModule) {
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
        this.originModule = originModule;
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
}

