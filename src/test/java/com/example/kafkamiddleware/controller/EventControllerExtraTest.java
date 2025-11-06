package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
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

class EventControllerExtraTest {

    static class TestTokenService extends TokenService {
        private final java.util.Map<String,Object> map = new java.util.HashMap<>();
        public void setResponse(String b, String c) { map.put(b,c); }
        public void setThrow(String b, String msg) { map.put(b, new TokenService.TokenValidationException(msg)); }
        @Override public String validateAndExtractClientId(String bearerToken) throws TokenValidationException { if (map.containsKey(bearerToken)) { Object v = map.get(bearerToken); if (v instanceof TokenService.TokenValidationException) throw (TokenService.TokenValidationException)v; return (String)v; } throw new TokenService.TokenValidationException("no token"); }
    }

    private TestTokenService tokenService;
    private ModuleRegistry moduleRegistry;
    private EventValidator eventValidator;
    private EventStore eventStore;
    private EventController controller;

    @BeforeEach
    void setUp() throws Exception {
        tokenService = new TestTokenService();
        moduleRegistry = mock(ModuleRegistry.class);
        eventValidator = mock(EventValidator.class);
        eventStore = new EventStore();
        java.lang.reflect.Field f = EventStore.class.getDeclaredField("storageType"); f.setAccessible(true); f.set(eventStore, "MEMORY");
        ModuleMessageStore moduleMessageStore = new ModuleMessageStore();
        CoreApiClient coreApiClient = mock(CoreApiClient.class);
        OriginMapper originMapper = mock(OriginMapper.class);
        AckService ackService = mock(AckService.class);
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, moduleMessageStore, new ObjectMapper(), coreApiClient, originMapper, ackService);
    }

    @Test
    void receiveEvent_originMismatch_usesTokenClientOrigin() {
        tokenService.setResponse("Bearer x","clientA");
        when(moduleRegistry.isAuthorizedModule("clientA")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        // set up origin mapper mock on controller's originMapper? We'll recreate controller with mocked originMapper locally
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("clientA")).thenReturn("Ventas");
        // rebuild controller with originMapper
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        Map<String,Object> body = Map.of("type","t","originModule","otherModule");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer x", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertEquals("clientA", e.getOriginModule());
    }

    @Test
    void receiveEvent_payloadStringInvalidJson_storedAsValue() {
        tokenService.setResponse("Bearer s","clientS");
        when(moduleRegistry.isAuthorizedModule("clientS")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("clientS")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        Map<String,Object> body = Map.of("type","t","payload","not-json");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer s", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertTrue(e.getPayload().containsKey("value"));
        assertEquals("not-json", e.getPayload().get("value"));
    }

    @Test
    void receiveEvent_payloadNull_becomesEmptyMap() {
        tokenService.setResponse("Bearer n","clientN");
        when(moduleRegistry.isAuthorizedModule("clientN")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("clientN")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        Map<String,Object> body = Map.of("type","t");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer n", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertTrue(e.getPayload().isEmpty());
    }

    @Test
    void listEvents_noStatus_returnsAll() {
        tokenService.setResponse("Bearer l","clientL");
        when(moduleRegistry.isAuthorizedModule("clientL")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());

        eventStore.save(new Event("t1", Map.of(), Instant.now(), "clientL", EventStatus.RECEIVED));
        eventStore.save(new Event("t2", Map.of(), Instant.now(), "clientL", EventStatus.DELIVERED));

        ResponseEntity<?> resp = controller.listEvents("Bearer l", null);
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(List.class, resp.getBody());
        List<?> list = (List<?>) resp.getBody();
        assertNotNull(list);
        assertEquals(2, list.size());
    }

    @Test
    void listEvents_statusCaseInsensitive() {
        tokenService.setResponse("Bearer lc","clientLc");
        when(moduleRegistry.isAuthorizedModule("clientLc")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());

        eventStore.save(new Event("t1", Map.of(), Instant.now(), "clientLc", EventStatus.RECEIVED));
        eventStore.save(new Event("t2", Map.of(), Instant.now(), "clientLc", EventStatus.DELIVERED));

        ResponseEntity<?> resp = controller.listEvents("Bearer lc", "rEcEiVed");
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(List.class, resp.getBody());
        List<?> list = (List<?>) resp.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void listEvents_unauthorizedToken_returns401() {
        tokenService.setThrow("Bearer bad","bad token");
        ResponseEntity<?> resp = controller.listEvents("Bearer bad", null);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void pollEvents_missingToken_returns401() {
        // no token -> TokenService will throw
        tokenService.setThrow(null, "no token");
        ResponseEntity<?> resp = controller.pollEvents(null);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void receiveEvent_moduleNotAuthorized_returns403() {
        tokenService.setResponse("Bearer z","clientZ");
        when(moduleRegistry.isAuthorizedModule("clientZ")).thenReturn(false);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());

        Map<String,Object> body = Map.of("type","t");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer z", body);
        assertEquals(403, resp.getStatusCode().value());
    }

    @Test
    void acknowledgeEvent_forwardThrows_isHandledAndResponseReturned() {
        String eventId = java.util.UUID.randomUUID().toString();
        tokenService.setResponse("Bearer a","clientA");
        when(moduleRegistry.isAuthorizedModule("clientA")).thenReturn(true);
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("clientA")).thenReturn("Ventas");

        com.example.kafkamiddleware.persistence.EventAckEntity ent = new com.example.kafkamiddleware.persistence.EventAckEntity();
        ent.setEventId(eventId);
        ent.setConsumer("Ventas");
        ent.setStatus("CONSUMED");
        ent.setAttempts(1);
        ent.setFirstSeenAt(Instant.now());
        ent.setLastSeenAt(Instant.now());

        AckService.AckResult result = new AckService.AckResult(ent, true);
        // need ackService mock in this test
        AckService ackService = mock(AckService.class);
        when(ackService.ack(eq(eventId), eq("Ventas"), any())).thenReturn(result);
        CoreApiClient coreApiClient = mock(CoreApiClient.class);
        doThrow(new RuntimeException("remote fail")).when(coreApiClient).forwardAckToCore(any());

        // rebuild controller with these mocks
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), coreApiClient, originMapper, ackService);

        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer a", eventId, null);
        // should still return CREATED despite remote forward failing
        assertEquals(201, resp.getStatusCode().value());
        verify(coreApiClient, times(1)).forwardAckToCore(any());
    }
}
