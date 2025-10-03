package com.example.kafkamiddleware.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "event_acks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_ack_event_consumer", columnNames = {"event_id", "consumer"})
})
public class EventAckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "consumer", length = 64, nullable = false)
    private String consumer;

    @Column(name = "status", length = 32, nullable = false)
    private String status; // e.g., CONSUMED

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getConsumer() { return consumer; }
    public void setConsumer(String consumer) { this.consumer = consumer; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}

