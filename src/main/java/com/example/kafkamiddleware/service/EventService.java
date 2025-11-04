package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.EventDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;
    private final ModuleMessageStore moduleMessageStore;

    // @Value("${spring.kafka.topic.core-events:core-events}")
    // private String coreTopic;

    @Value("${core.api.url:http://localhost:8082/api}")
    private String coreApiBase;

    @Value("${core.api.resource-path:/core/events}")
    private String coreApiPath;

    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled;

    public EventService(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider, ObjectMapper objectMapper, ModuleMessageStore moduleMessageStore) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper = objectMapper;
        this.moduleMessageStore = moduleMessageStore;
    }

    public void sendToCore(EventDto event) throws EventSendException {
        try {
            if (kafkaEnabled) {
                KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
                if (kafkaTemplate == null) {
                    // Defensive: kafka enabled but no bean available
                    throw new EventSendException("Kafka is enabled but KafkaTemplate bean is missing", null);
                }
                String payload = objectMapper.writeValueAsString(event);
                // Use originModule as key so Core can route or partition if needed
                String coreEndpoint = coreApiBase+coreApiPath;
                System.out.println(coreEndpoint);
                kafkaTemplate.send(coreEndpoint, event.getOriginModule(), payload);
            } else {
                // For local testing without Kafka: simulate Core by directly storing the event for the originModule
                moduleMessageStore.addMessageForModule(event.getOriginModule(), event);
            }
        } catch (JsonProcessingException e) {
            throw new EventSendException("Failed to serialize event", e);
        } catch (Exception e) {
            throw new EventSendException("Failed to send event: " + e.getMessage(), e);
        }
    }

    public static class EventSendException extends Exception {
        public EventSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
