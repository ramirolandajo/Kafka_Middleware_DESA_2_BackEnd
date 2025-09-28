package com.example.kafkamiddleware.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EventValidator {

    private final ObjectMapper objectMapper;
    private JsonSchema schema;

    public EventValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws Exception {
        ClassPathResource res = new ClassPathResource("event-schema.json");
        try (InputStream is = res.getInputStream()) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonNode node = objectMapper.readTree(is);
            schema = factory.getSchema(node);
        }
    }

    public Set<String> validate(Map<String, Object> payload) {
        JsonNode node = objectMapper.valueToTree(payload);
        Set<ValidationMessage> errors = schema.validate(node);
        if (errors == null || errors.isEmpty()) return Set.of();
        return errors.stream().map(ValidationMessage::getMessage).collect(Collectors.toSet());
    }
}

