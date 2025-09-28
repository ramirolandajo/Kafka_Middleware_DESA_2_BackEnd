package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.persistence.EventRepository;
import com.example.kafkamiddleware.service.EventStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/_debug")
public class DebugController {

    private final EventStore eventStore;
    private final EventRepository repository; // puede ser null si no hay JPA

    @Value("${app.storage.type:DATABASE}")
    private String storageTypeProp;

    public DebugController(EventStore eventStore, EventRepository repository) {
        this.eventStore = eventStore;
        this.repository = repository;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        boolean repoAvailable = repository != null;
        boolean effectiveDb = !"MEMORY".equalsIgnoreCase(storageTypeProp) && repoAvailable;
        long dbCount = repoAvailable ? repository.count() : -1L;
        int totalCount = eventStore.listAll().size();
        Map<String, Object> out = new HashMap<>();
        out.put("storageTypeProperty", storageTypeProp);
        out.put("repositoryAvailable", repoAvailable);
        out.put("effectiveMode", effectiveDb ? "DATABASE" : "MEMORY");
        out.put("dbCount", dbCount);
        out.put("totalCount", totalCount);
        return out;
    }
}

