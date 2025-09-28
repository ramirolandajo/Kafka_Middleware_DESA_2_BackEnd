package com.example.kafkamiddleware.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class OriginMapper {

    // Formato: "ecommerce-app:Ventas,inventory-service:Inventario,analytics-service:Analitica"
    @Value("${app.origin.map:ecommerce-app:Ventas,inventory-service:Inventario,analytics-service:Analitica}")
    private String mappingProperty;

    private final Map<String, String> mapping = new HashMap<>();

    @PostConstruct
    public void init() {
        if (mappingProperty == null || mappingProperty.isBlank()) return;
        Arrays.stream(mappingProperty.split(","))
                .map(String::trim)
                .filter(s -> s.contains(":"))
                .forEach(pair -> {
                    String[] kv = pair.split(":", 2);
                    mapping.put(kv[0].trim(), kv[1].trim());
                });
    }

    public String map(String clientId) {
        return mapping.getOrDefault(clientId, clientId);
    }
}

