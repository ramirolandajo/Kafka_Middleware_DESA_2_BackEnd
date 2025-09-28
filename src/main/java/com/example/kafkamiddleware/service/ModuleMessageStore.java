package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.EventDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class ModuleMessageStore {

    private final ConcurrentHashMap<String, Queue<EventDto>> store = new ConcurrentHashMap<>();

    public void addMessageForModule(String moduleId, EventDto event) {
        store.computeIfAbsent(moduleId, k -> new ConcurrentLinkedQueue<>()).add(event);
    }

    public List<EventDto> pollMessagesForModule(String moduleId) {
        Queue<EventDto> q = store.get(moduleId);
        List<EventDto> out = new ArrayList<>();
        if (q == null) return out;
        EventDto e;
        while ((e = q.poll()) != null) {
            out.add(e);
        }
        return out;
    }
}

