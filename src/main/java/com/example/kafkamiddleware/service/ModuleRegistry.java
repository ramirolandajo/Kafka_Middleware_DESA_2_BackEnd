package com.example.kafkamiddleware.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ModuleRegistry {

    @Value("${app.authorized-modules:}")
    private String authorizedModulesProperty;

    private Set<String> authorizedModules = new HashSet<>();

    @PostConstruct
    public void init() {
        if (authorizedModulesProperty != null && !authorizedModulesProperty.isBlank()) {
            authorizedModules = Arrays.stream(authorizedModulesProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
    }

    public boolean isAuthorizedModule(String clientId) {
        if (authorizedModules.isEmpty()) return false;
        return authorizedModules.contains(clientId);
    }

    public Set<String> getAuthorizedModules() {
        return Set.copyOf(authorizedModules);
    }
}

