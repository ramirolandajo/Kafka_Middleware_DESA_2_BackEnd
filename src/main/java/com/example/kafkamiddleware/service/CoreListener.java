package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.EventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class CoreListener {

    private final ObjectMapper objectMapper;
    private final ModuleMessageStore moduleMessageStore;

    public CoreListener(ObjectMapper objectMapper, ModuleMessageStore moduleMessageStore) {
        this.objectMapper = objectMapper;
        this.moduleMessageStore = moduleMessageStore;
    }

    @KafkaListener(topics = "${spring.kafka.topic.core-events:core-events}", groupId = "${spring.kafka.consumer.group-id:kafka-middleware-group}")
    public void listen(String message) {
        try {
            EventDto event = objectMapper.readValue(message, EventDto.class);
            if (event.getOriginModule() != null) {
                // deliver to the module specified in originModule
                moduleMessageStore.addMessageForModule(event.getOriginModule(), event);
            }
        } catch (Exception e) {
            // log and swallow - middleware must not crash on bad messages
            System.err.println("Failed to handle incoming core message: " + e.getMessage());
        }
    }
}
