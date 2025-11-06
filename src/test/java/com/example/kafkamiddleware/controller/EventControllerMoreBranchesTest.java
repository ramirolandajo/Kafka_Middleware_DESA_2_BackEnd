package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventControllerMoreBranchesTest {

    static class TestTokenService extends TokenService {
        private final java.util.Map<String,Object> map = new java.util.HashMap<>();
        public void setResponse(String b, String c) { map.put(b,c); }
        @SuppressWarnings("unused")
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
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), mock(OriginMapper.class), mock(AckService.class));
    }

    @Test
    void receiveEvent_timestampStringNumeric_parsesEpochMillis() {
        long nowMillis = System.currentTimeMillis();
        tokenService.setResponse("Bearer tnum", "mc");
        when(moduleRegistry.isAuthorizedModule("mc")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mc")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        Map<String,Object> body = Map.of("type","evt","timestamp", String.valueOf(nowMillis));
        ResponseEntity<?> resp = controller.receiveEvent("Bearer tnum", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertEquals(Instant.ofEpochMilli(nowMillis), e.getTimestamp());
    }

    @Test
    void receiveEvent_timestampIso_parsesIso() {
        String iso = "2020-01-01T00:00:00Z";
        tokenService.setResponse("Bearer tiso", "mi");
        when(moduleRegistry.isAuthorizedModule("mi")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mi")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        Map<String,Object> body = Map.of("type","evt","timestamp", iso);
        ResponseEntity<?> resp = controller.receiveEvent("Bearer tiso", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertEquals(Instant.parse(iso), e.getTimestamp());
    }

    @Test
    void receiveEvent_timestampShortList_becomesNow() {
        tokenService.setResponse("Bearer tlist", "ml");
        when(moduleRegistry.isAuthorizedModule("ml")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("ml")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        List<Integer> shortList = List.of(2020,1);
        Instant before = Instant.now();
        Map<String,Object> body = Map.of("type","evt","timestamp", shortList);
        ResponseEntity<?> resp = controller.receiveEvent("Bearer tlist", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        Instant after = Instant.now();
        assertTrue(!e.getTimestamp().isBefore(before) && !e.getTimestamp().isAfter(after));
    }

    @Test
    void receiveEvent_blankType_returnsBadRequest() {
        tokenService.setResponse("Bearer bt", "mb");
        when(moduleRegistry.isAuthorizedModule("mb")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());

        Map<String,Object> body = Map.of("type"," ");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer bt", body);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void pollEvents_forbidden_whenModuleNotAuthorized() {
        tokenService.setResponse("Bearer p","mp");
        when(moduleRegistry.isAuthorizedModule("mp")).thenReturn(false);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());

        ResponseEntity<?> resp = controller.pollEvents("Bearer p");
        assertEquals(403, resp.getStatusCode().value());
    }

    @Test
    void acknowledgeEvent_put_delegatesToPost() {
        String eventId = java.util.UUID.randomUUID().toString();
        tokenService.setResponse("Bearer put","mc");
        when(moduleRegistry.isAuthorizedModule("mc")).thenReturn(true);
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mc")).thenReturn("Ventas");

        com.example.kafkamiddleware.persistence.EventAckEntity ent = new com.example.kafkamiddleware.persistence.EventAckEntity();
        ent.setEventId(eventId);
        ent.setConsumer("Ventas");
        ent.setStatus("CONSUMED");
        ent.setAttempts(1);
        ent.setFirstSeenAt(Instant.now());
        ent.setLastSeenAt(Instant.now());

        AckService.AckResult result = new AckService.AckResult(ent, false);
        AckService ackService = mock(AckService.class);
        when(ackService.ack(eq(eventId), eq("Ventas"), any())).thenReturn(result);
        CoreApiClient coreApiClient = mock(CoreApiClient.class);

        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), coreApiClient, originMapper, ackService);

        ResponseEntity<?> resp = controller.acknowledgeEventPut("Bearer put", eventId, null);
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
    }

    @Test
    void acknowledgeEvent_ackServiceThrows_propagates() {
        String eventId = java.util.UUID.randomUUID().toString();
        tokenService.setResponse("Bearer exc","mexc");
        when(moduleRegistry.isAuthorizedModule("mexc")).thenReturn(true);
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mexc")).thenReturn("Ventas");

        AckService ackService = mock(AckService.class);
        when(ackService.ack(eq(eventId), anyString(), any())).thenThrow(new RuntimeException("boom"));
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, ackService);

        assertThrows(RuntimeException.class, () -> controller.acknowledgeEvent("Bearer exc", eventId, null));
    }

    @Test
    void receiveEvent_validatorErrors_returnBadRequest() {
        tokenService.setResponse("Bearer ve","mv");
        when(moduleRegistry.isAuthorizedModule("mv")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of("err"));

        Map<String,Object> body = Map.of("type","t");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer ve", body);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void acknowledgeEvent_consumedAt_epochMillis_isPassedToAckService() {
        String eventId = java.util.UUID.randomUUID().toString();
        long nowMillis = System.currentTimeMillis();

        tokenService.setResponse("Bearer a1", "mc1");
        when(moduleRegistry.isAuthorizedModule("mc1")).thenReturn(true);
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mc1")).thenReturn("Ventas");

        AckService ackService = mock(AckService.class);
        // return some dummy result
        com.example.kafkamiddleware.persistence.EventAckEntity ent = new com.example.kafkamiddleware.persistence.EventAckEntity();
        ent.setEventId(eventId); ent.setConsumer("Ventas"); ent.setStatus("CONSUMED"); ent.setAttempts(1); ent.setFirstSeenAt(Instant.ofEpochMilli(nowMillis)); ent.setLastSeenAt(Instant.ofEpochMilli(nowMillis));
        when(ackService.ack(eq(eventId), eq("Ventas"), any())).thenReturn(new AckService.AckResult(ent, true));

        CoreApiClient coreApiClient = mock(CoreApiClient.class);
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), coreApiClient, originMapper, ackService);

        Map<String,Object> body = Map.of("consumedAt", nowMillis);
        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer a1", eventId, body);
        assertEquals(201, resp.getStatusCode().value());

        ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
        verify(ackService).ack(eq(eventId), eq("Ventas"), cap.capture());
        assertEquals(Instant.ofEpochMilli(nowMillis), cap.getValue());
    }

    @Test
    void acknowledgeEvent_consumedAt_isoString_isPassedToAckService() {
        String eventId = java.util.UUID.randomUUID().toString();
        String iso = "2020-01-01T00:00:00Z";

        tokenService.setResponse("Bearer a2", "mc2");
        when(moduleRegistry.isAuthorizedModule("mc2")).thenReturn(true);
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mc2")).thenReturn("Ventas");

        AckService ackService = mock(AckService.class);
        com.example.kafkamiddleware.persistence.EventAckEntity ent = new com.example.kafkamiddleware.persistence.EventAckEntity();
        ent.setEventId(eventId); ent.setConsumer("Ventas"); ent.setStatus("CONSUMED"); ent.setAttempts(1); ent.setFirstSeenAt(Instant.parse(iso)); ent.setLastSeenAt(Instant.parse(iso));
        when(ackService.ack(eq(eventId), eq("Ventas"), any())).thenReturn(new AckService.AckResult(ent, true));

        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, ackService);

        Map<String,Object> body = Map.of("consumedAt", iso);
        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer a2", eventId, body);
        assertEquals(201, resp.getStatusCode().value());

        ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
        verify(ackService).ack(eq(eventId), eq("Ventas"), cap.capture());
        assertEquals(Instant.parse(iso), cap.getValue());
    }

    @Test
    void acknowledgeEvent_consumedAt_listWithNanos_isPassedToAckService() {
        String eventId = java.util.UUID.randomUUID().toString();
        List<Integer> ts = List.of(2020, 1, 2, 3, 4, 5, 123000000);
        LocalDateTime ldt = LocalDateTime.of(2020,1,2,3,4,5,123000000);
        Instant expected = ldt.toInstant(ZoneOffset.UTC);

        tokenService.setResponse("Bearer a3", "mc3");
        when(moduleRegistry.isAuthorizedModule("mc3")).thenReturn(true);
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mc3")).thenReturn("Ventas");

        AckService ackService = mock(AckService.class);
        com.example.kafkamiddleware.persistence.EventAckEntity ent = new com.example.kafkamiddleware.persistence.EventAckEntity();
        ent.setEventId(eventId); ent.setConsumer("Ventas"); ent.setStatus("CONSUMED"); ent.setAttempts(1); ent.setFirstSeenAt(expected); ent.setLastSeenAt(expected);
        when(ackService.ack(eq(eventId), eq("Ventas"), any())).thenReturn(new AckService.AckResult(ent, true));

        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, ackService);

        Map<String,Object> body = Map.of("consumedAt", ts);
        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer a3", eventId, body);
        assertEquals(201, resp.getStatusCode().value());

        ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
        verify(ackService).ack(eq(eventId), eq("Ventas"), cap.capture());
        assertEquals(expected, cap.getValue());
    }

    // language: java
    @Test
    void receiveEvent_payloadAsList_convertsViaObjectMapper() {
        tokenService.setResponse("Bearer pl", "mpl");
        when(moduleRegistry.isAuthorizedModule("mpl")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mpl")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        List<Object> payload = List.of(Map.of("a",1), Map.of("b",2));
        // envolver la lista dentro de un Map para que ObjectMapper.convertValue pueda convertir a Map sin error
        Map<String,Object> body = Map.of("type","t","payload", Map.of("items", payload));
        ResponseEntity<?> resp = controller.receiveEvent("Bearer pl", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertFalse(e.getPayload().isEmpty());
    }

    @Test
    void receiveEvent_payloadAsNumber_convertsViaObjectMapper() {
        tokenService.setResponse("Bearer pn", "mpn");
        when(moduleRegistry.isAuthorizedModule("mpn")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("mpn")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        // envolver el número dentro de un Map para evitar la excepción de deserialización
        Map<String,Object> body = Map.of("type","t","payload", Map.of("value", 12345));
        ResponseEntity<?> resp = controller.receiveEvent("Bearer pn", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertFalse(e.getPayload().isEmpty());
    }

    @Test
    void receiveEvent_timestampListWithNanos_parsesNanosCorrectly() {
        tokenService.setResponse("Bearer t7", "m7");
        when(moduleRegistry.isAuthorizedModule("m7")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        OriginMapper originMapper = mock(OriginMapper.class);
        when(originMapper.map("m7")).thenReturn("Ventas");
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, new ModuleMessageStore(), new ObjectMapper(), mock(CoreApiClient.class), originMapper, mock(AckService.class));

        List<Integer> ts = List.of(2021,12,3,4,5,6, 789000000);
        LocalDateTime ldt = LocalDateTime.of(2021,12,3,4,5,6,789000000);
        Instant expected = ldt.toInstant(ZoneOffset.UTC);

        Map<String,Object> body = Map.of("type","t","timestamp", ts);
        ResponseEntity<?> resp = controller.receiveEvent("Bearer t7", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertNotNull(respBody.get("eventId"));
        String id = (String) respBody.get("eventId");
        Event e = eventStore.findById(id);
        assertNotNull(e);
        assertEquals(expected, e.getTimestamp());
    }

}
