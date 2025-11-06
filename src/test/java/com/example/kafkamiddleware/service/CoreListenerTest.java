package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.EventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CoreListenerTest {

    private ObjectMapper newMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    @Test
    void listen_validMessage_addsToModuleStore() throws Exception {
        ObjectMapper mapper = newMapper();
        ModuleMessageStore store = new ModuleMessageStore();
        CoreListener listener = new CoreListener(mapper, store);

        EventDto dto = new EventDto("t1", java.util.Map.of("k", "v"), Instant.now(), "modZ");
        String json = mapper.writeValueAsString(dto);

        listener.listen(json);

        List<EventDto> polled = store.pollMessagesForModule("modZ");
        assertEquals(1, polled.size());
        assertEquals("t1", polled.get(0).getType());
    }

    @Test
    void listen_invalidMessage_doesNotThrowAndDoesNotAdd() {
        ObjectMapper mapper = newMapper();
        ModuleMessageStore store = new ModuleMessageStore();
        CoreListener listener = new CoreListener(mapper, store);

        // invalid JSON
        listener.listen("not a json");

        List<EventDto> polled = store.pollMessagesForModule("any");
        assertTrue(polled.isEmpty());
    }

    @Test
    void listen_messageWithoutOrigin_isIgnored() throws Exception {
        ObjectMapper mapper = newMapper();
        ModuleMessageStore store = new ModuleMessageStore();
        CoreListener listener = new CoreListener(mapper, store);

        // Create a DTO without originModule
        EventDto dto = new EventDto("t2", Map.of(), Instant.now(), null);
        String json = mapper.writeValueAsString(dto);

        listener.listen(json);

        // Ensure no messages are added to any module queue
        assertTrue(store.pollMessagesForModule("any").isEmpty());
    }
}
