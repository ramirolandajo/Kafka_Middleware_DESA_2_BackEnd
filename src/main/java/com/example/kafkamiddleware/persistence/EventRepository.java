package com.example.kafkamiddleware.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<EventEntity, String> {
    Optional<EventEntity> findBySignature(String signature);
    List<EventEntity> findByStatus(String status);
}

