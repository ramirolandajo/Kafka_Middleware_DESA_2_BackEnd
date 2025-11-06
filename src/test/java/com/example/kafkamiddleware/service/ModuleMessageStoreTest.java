package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.EventDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModuleMessageStoreTest {

    @Test
    void addAndPoll_messagesShouldBeStoredAndPolled() {
        ModuleMessageStore store = new ModuleMessageStore();
        EventDto e = new EventDto("type1", Map.of("k", "v"), Instant.now(), "module-a");

        store.addMessageForModule("module-a", e);
        List<EventDto> polled = store.pollMessagesForModule("module-a");

        assertNotNull(polled);
        assertEquals(1, polled.size());
        assertEquals("type1", polled.get(0).getType());

        // subsequent poll should be empty
        List<EventDto> second = store.pollMessagesForModule("module-a");
        assertTrue(second.isEmpty());
    }

    @Test
    void poll_multipleModules_areIndependent() {
        ModuleMessageStore store = new ModuleMessageStore();
        EventDto e1 = new EventDto("t1", Map.of(), Instant.now(), "mod-a");
        EventDto e2 = new EventDto("t2", Map.of(), Instant.now(), "mod-b");

        store.addMessageForModule("mod-a", e1);
        store.addMessageForModule("mod-b", e2);

        List<EventDto> polledA = store.pollMessagesForModule("mod-a");
        assertEquals(1, polledA.size());
        assertEquals("t1", polledA.get(0).getType());

        List<EventDto> polledB = store.pollMessagesForModule("mod-b");
        assertEquals(1, polledB.size());
        assertEquals("t2", polledB.get(0).getType());
    }

    @Test
    void poll_emptyModule_returnsEmptyList() {
        ModuleMessageStore store = new ModuleMessageStore();
        List<EventDto> polled = store.pollMessagesForModule("non-existent-module");
        assertNotNull(polled);
        assertTrue(polled.isEmpty());
    }
}
