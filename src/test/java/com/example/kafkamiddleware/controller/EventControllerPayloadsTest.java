package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventControllerPayloadsTest {
    @BeforeEach
    void setUp() throws Exception {
        // no-op here, per-test setup below
    }

    @Test
    void receiveEvent_payloadAsString_parsedToMap() {
        TestTokenService tokenService = new TestTokenService();
        tokenService.setResponse("Bearer s", "m1");
        ModuleRegistry moduleRegistry = mock(ModuleRegistry.class);
        when(moduleRegistry.isAuthorizedModule("m1")).thenReturn(true);
        EventValidator eventValidator = mock(EventValidator.class);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("m1")).thenReturn("C");

        EventStore eventStore = new EventStore();
        try { java.lang.reflect.Field f = EventStore.class.getDeclaredField("storageType"); f.setAccessible(true); f.set(eventStore, "MEMORY"); } catch (Exception e) { throw new RuntimeException(e); }

        ModuleMessageStore moduleMessageStore = new ModuleMessageStore();
        CoreApiClient coreApiClient = mock(CoreApiClient.class);
        AckService ackService = mock(AckService.class);

        EventController controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, moduleMessageStore, new ObjectMapper(), coreApiClient, originMapper, ackService);

        Map<String,Object> body = Map.of("type","t","payload","{\"a\":2}");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer s", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertTrue(e.getPayload().containsKey("a"));
    }

    @Test
    void receiveEvent_timestampAsArray_parsed() {
        TestTokenService tokenService = new TestTokenService();
        tokenService.setResponse("Bearer t", "m2");
        ModuleRegistry moduleRegistry = mock(ModuleRegistry.class);
        when(moduleRegistry.isAuthorizedModule("m2")).thenReturn(true);
        EventValidator eventValidator = mock(EventValidator.class);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("m2")).thenReturn("C");

        EventStore eventStore = new EventStore();
        try { java.lang.reflect.Field f = EventStore.class.getDeclaredField("storageType"); f.setAccessible(true); f.set(eventStore, "MEMORY"); } catch (Exception e) { throw new RuntimeException(e); }

        ModuleMessageStore moduleMessageStore = new ModuleMessageStore();
        CoreApiClient coreApiClient = mock(CoreApiClient.class);
        AckService ackService = mock(AckService.class);

        EventController controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, moduleMessageStore, new ObjectMapper(), coreApiClient, originMapper, ackService);

        List<Integer> ts = List.of(2020,1,2,3,4,5);
        Map<String,Object> body = Map.of("type","t","timestamp", ts);
        ResponseEntity<?> resp = controller.receiveEvent("Bearer t", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e.getTimestamp());
    }

    // lightweight test token service
    static class TestTokenService extends TokenService {
        private final Map<String,Object> map = new java.util.HashMap<>();
        public void setResponse(String b, String c) { map.put(b,c); }
        @Override public String validateAndExtractClientId(String bearerToken) throws TokenValidationException { if (map.containsKey(bearerToken)) return (String)map.get(bearerToken); throw new TokenValidationException("no"); }
    }
}
