package com.example.kafkamiddleware.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_events_signature", columnNames = {"signature"})
})
public class EventEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String type;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String originModule;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, unique = true, length = 128)
    private String signature;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getOriginModule() { return originModule; }
    public void setOriginModule(String originModule) { this.originModule = originModule; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
