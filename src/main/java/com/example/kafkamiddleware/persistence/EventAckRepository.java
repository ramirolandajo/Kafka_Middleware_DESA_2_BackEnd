package com.example.kafkamiddleware.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventAckRepository extends JpaRepository<EventAckEntity, Long> {
    Optional<EventAckEntity> findByEventIdAndConsumer(String eventId, String consumer);
}

