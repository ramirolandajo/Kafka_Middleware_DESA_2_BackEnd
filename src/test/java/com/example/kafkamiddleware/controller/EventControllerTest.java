package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import com.example.kafkamiddleware.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventControllerTest {

    private TokenService tokenService;
    private ModuleRegistry moduleRegistry;
    private EventValidator eventValidator;
    private EventStore eventStore;
    private ModuleMessageStore moduleMessageStore;
    // moduleMessageStore and objectmapper used only in setup -> keep local
    private CoreApiClient coreApiClient;
    private OriginMapper originMapper;
    private AckService ackService;
    private EventController controller;

    static class TestTokenService extends TokenService {
        private final Map<String, Object> responses = new HashMap<>();

        public void setResponse(String bearerToken, String clientId) {
            responses.put(bearerToken, clientId);
        }

        public void setThrow(String bearerToken, String message) {
            responses.put(bearerToken, new TokenService.TokenValidationException(message));
        }

        @Override
        public String validateAndExtractClientId(String bearerToken) throws TokenValidationException {
            if (responses.containsKey(bearerToken)) {
                Object v = responses.get(bearerToken);
                if (v instanceof TokenService.TokenValidationException) {
                    throw (TokenService.TokenValidationException) v;
                }
                return (String) v;
            }
            throw new TokenService.TokenValidationException("Token not configured in test: " + bearerToken);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // use test double for TokenService to avoid inline-mock/ByteBuddy issues on newer JVMs
        tokenService = new TestTokenService();
        moduleRegistry = mock(ModuleRegistry.class);
        eventValidator = mock(EventValidator.class);
        eventStore = new EventStore();
        // force memory
        java.lang.reflect.Field f = EventStore.class.getDeclaredField("storageType");
        f.setAccessible(true);
        f.set(eventStore, "MEMORY");
        ObjectMapper objectMapper = new ObjectMapper();
        moduleMessageStore = new ModuleMessageStore();
        coreApiClient = mock(CoreApiClient.class);
        originMapper = mock(OriginMapper.class);
        ackService = mock(AckService.class);

        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, moduleMessageStore, objectMapper, coreApiClient, originMapper, ackService);
    }

    private com.example.kafkamiddleware.persistence.EventAckEntity createMockAckEntity(String eventId, String consumer) {
        com.example.kafkamiddleware.persistence.EventAckEntity ent = new com.example.kafkamiddleware.persistence.EventAckEntity();
        ent.setEventId(eventId);
        ent.setConsumer(consumer);
        ent.setStatus("CONSUMED");
        ent.setAttempts(1);
        ent.setFirstSeenAt(Instant.now());
        ent.setLastSeenAt(Instant.now());
        return ent;
    }

    @Test
    void receiveEvent_successfulFlow_returnsAcceptedAndStoresEvent() {
        Map<String, Object> body = Map.of("type", "evt-type", "payload", Map.of("a",1), "originModule", "modX");
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer abc", "modX");
        when(moduleRegistry.isAuthorizedModule("modX")).thenReturn(true);
        when(originMapper.map("modX")).thenReturn("Ventas");

        ResponseEntity<?> resp = controller.receiveEvent("Bearer abc", body);
        assertEquals(202, resp.getStatusCode().value());
        Map<?,?> bodyResp = (Map<?,?>) resp.getBody();
        assertNotNull(bodyResp);
        assertEquals("received", bodyResp.get("status"));
        String id = (String) bodyResp.get("eventId");
        assertNotNull(id);

        // ensure eventStore has it
        Event found = eventStore.findById(id);
        assertNotNull(found);
        assertEquals(EventStatus.RECEIVED, found.getStatus());

        // coreApiClient.forwardAsync should be called
        verify(coreApiClient, times(1)).forwardAsync(any(Event.class), eq("Ventas"));
    }

    @Test
    void receiveEvent_schemaInvalid_returnsBadRequest() {
        Map<String, Object> body = Map.of("payload", Map.of("a",1));
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of("error"));

        ResponseEntity<?> resp = controller.receiveEvent(null, body);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void receiveEvent_tokenInvalid_returnsUnauthorized() {
        Map<String, Object> body = Map.of("type", "evt-type");
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setThrow(null, "Authorization token missing");

        ResponseEntity<?> resp = controller.receiveEvent(null, body);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void pollEvents_authorized_returnsMessages() {
        ((TestTokenService) tokenService).setResponse("Bearer tok", "modP");
        when(moduleRegistry.isAuthorizedModule("modP")).thenReturn(true);

        com.example.kafkamiddleware.dto.EventDto dto = new com.example.kafkamiddleware.dto.EventDto("t1", Map.of("x",1), Instant.now(), "modP");
        moduleMessageStore.addMessageForModule("modP", dto);

        ResponseEntity<?> resp = controller.pollEvents("Bearer tok");
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(List.class, resp.getBody());
        List<?> msgList = (List<?>) resp.getBody();
        assertEquals(1, msgList.size());
    }

    @Test
    void listEvents_withStatus_filtersCorrectly() {
        ((TestTokenService) tokenService).setResponse("Bearer tok2", "modL");
        when(moduleRegistry.isAuthorizedModule("modL")).thenReturn(true);

        Event e1 = new Event("t1", Map.of(), Instant.now(), "modL", EventStatus.RECEIVED);
        Event e2 = new Event("t2", Map.of(), Instant.now(), "modL", EventStatus.DELIVERED);
        eventStore.save(e1);
        eventStore.save(e2);

        ResponseEntity<?> resp = controller.listEvents("Bearer tok2", "delivered");
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(List.class, resp.getBody());
        List<?> list = (List<?>) resp.getBody();
        assertEquals(1, list.size());
    }

    @Test
    void acknowledgeEvent_createdAndForwarded() {
        String eventId = java.util.UUID.randomUUID().toString();
        ((TestTokenService) tokenService).setResponse("Bearer ack", "modA");
        when(moduleRegistry.isAuthorizedModule("modA")).thenReturn(true);
        when(originMapper.map("modA")).thenReturn("Ventas");

        com.example.kafkamiddleware.persistence.EventAckEntity ent = createMockAckEntity(eventId, "Ventas");

        AckService.AckResult result = new AckService.AckResult(ent, true);
        when(ackService.ack(eq(eventId), eq("Ventas"), any())).thenReturn(result);

        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer ack", eventId, null);
        assertEquals(201, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        assertNotNull(resp.getBody());
        Map<?,?> body = (Map<?,?>) resp.getBody();
        assertNotNull(body.get("eventId"));
        assertNotNull(body.get("consumer"));
        assertEquals(eventId, body.get("eventId"));
        assertEquals("Ventas", body.get("consumer"));

        verify(coreApiClient, times(1)).forwardAckToCore(any());
    }

    @Test
    void acknowledgeEvent_existingAck_returnsOk() {
        String eventId = java.util.UUID.randomUUID().toString();
        ((TestTokenService) tokenService).setResponse("Bearer ack2", "modB");
        when(moduleRegistry.isAuthorizedModule("modB")).thenReturn(true);
        when(originMapper.map("modB")).thenReturn("Analitica");

        com.example.kafkamiddleware.persistence.EventAckEntity ent = createMockAckEntity(eventId, "Analitica");
        ent.setAttempts(2);

        AckService.AckResult result = new AckService.AckResult(ent, false);
        when(ackService.ack(eq(eventId), eq("Analitica"), any())).thenReturn(result);

        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer ack2", eventId, null);
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        assertNotNull(resp.getBody());
        Map<?,?> body = (Map<?,?>) resp.getBody();
        assertNotNull(body.get("eventId"));
        assertNotNull(body.get("consumer"));
        assertEquals(eventId, body.get("eventId"));
        assertEquals("Analitica", body.get("consumer"));

        verify(coreApiClient, times(1)).forwardAckToCore(any());
    }

    @Test
    void listEvents_invalidStatus_returnsBadRequest() {
        ((TestTokenService) tokenService).setResponse("Bearer tok3", "modL");
        when(moduleRegistry.isAuthorizedModule("modL")).thenReturn(true);

        ResponseEntity<?> resp = controller.listEvents("Bearer tok3", "notastatus");
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void pollEvents_unauthorized_returnsForbidden() {
        ((TestTokenService) tokenService).setResponse("Bearer bad", "modX");
        when(moduleRegistry.isAuthorizedModule("modX")).thenReturn(false);

        ResponseEntity<?> resp = controller.pollEvents("Bearer bad");
        assertEquals(403, resp.getStatusCode().value());
    }

    @Test
    void acknowledgeEvent_invalidEventId_returnsBadRequest() {
        ((TestTokenService) tokenService).setResponse("Bearer a3", "modC");
        when(moduleRegistry.isAuthorizedModule("modC")).thenReturn(true);

        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer a3", "not-a-uuid", null);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void receiveEvent_payloadAsJsonString_parsesCorrectly() {
        String payloadJson = "{\"key\":\"value\"}";
        Map<String, Object> body = Map.of("type", "evt-json", "payload", payloadJson);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer json", "modJ");
        when(moduleRegistry.isAuthorizedModule("modJ")).thenReturn(true);
        when(originMapper.map("modJ")).thenReturn("Inventario");

        ResponseEntity<?> resp = controller.receiveEvent("Bearer json", body);
        assertEquals(202, resp.getStatusCode().value());
        String id = (String) ((Map<?,?>) resp.getBody()).get("eventId");
        Event found = eventStore.findById(id);
        assertNotNull(found);
        assertEquals(Map.of("key", "value"), found.getPayload());
    }

    @Test
    void receiveEvent_payloadAsSimpleString_wrapsInMap() {
        String payload = "just a string";
        Map<String, Object> body = Map.of("type", "evt-str", "payload", payload);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer str", "modS");
        when(moduleRegistry.isAuthorizedModule("modS")).thenReturn(true);
        when(originMapper.map("modS")).thenReturn("Ventas");

        ResponseEntity<?> resp = controller.receiveEvent("Bearer str", body);
        assertEquals(202, resp.getStatusCode().value());
        String id = (String) ((Map<?,?>) resp.getBody()).get("eventId");
        Event found = eventStore.findById(id);
        assertNotNull(found);
        assertEquals(Map.of("value", "just a string"), found.getPayload());
    }

    @Test
    void receiveEvent_originModuleMismatch_usesTokenClientId() {
        Map<String, Object> body = Map.of("type", "evt-mismatch", "originModule", "modY");
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer abc", "modX");
        when(moduleRegistry.isAuthorizedModule("modX")).thenReturn(true);

        ResponseEntity<?> resp = controller.receiveEvent("Bearer abc", body);
        assertEquals(202, resp.getStatusCode().value());
        String id = (String) ((Map<?,?>) resp.getBody()).get("eventId");
        Event found = eventStore.findById(id);
        assertNotNull(found);
        assertEquals("modX", found.getOriginModule());
    }

    @Test
    void receiveEvent_numericTimestamp_parsesCorrectly() {
        long ts = Instant.now().toEpochMilli();
        Map<String, Object> body = Map.of("type", "evt-ts", "timestamp", ts);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer ts", "modT");
        when(moduleRegistry.isAuthorizedModule("modT")).thenReturn(true);

        ResponseEntity<?> resp = controller.receiveEvent("Bearer ts", body);
        assertEquals(202, resp.getStatusCode().value());
        String id = (String) ((Map<?,?>) resp.getBody()).get("eventId");
        Event found = eventStore.findById(id);
        assertNotNull(found);
        assertEquals(ts, found.getTimestamp().toEpochMilli());
    }

    @Test
    void receiveEvent_listTimestamp_parsesCorrectly() {
        List<Integer> ts = List.of(2024, 5, 20, 10, 30, 0, 0);
        Map<String, Object> body = Map.of("type", "evt-ts-list", "timestamp", ts);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer ts-list", "modT");
        when(moduleRegistry.isAuthorizedModule("modT")).thenReturn(true);

        ResponseEntity<?> resp = controller.receiveEvent("Bearer ts-list", body);
        assertEquals(202, resp.getStatusCode().value());
        String id = (String) ((Map<?,?>) resp.getBody()).get("eventId");
        Event found = eventStore.findById(id);
        assertNotNull(found);
        Instant expected = java.time.LocalDateTime.of(2024, 5, 20, 10, 30, 0).toInstant(java.time.ZoneOffset.UTC);
        assertEquals(expected, found.getTimestamp());
    }

    @Test
    void listEvents_noStatus_returnsAll() {
        ((TestTokenService) tokenService).setResponse("Bearer listall", "modL");
        when(moduleRegistry.isAuthorizedModule("modL")).thenReturn(true);
        eventStore.save(new Event("t1", Map.of(), Instant.now(), "modL", EventStatus.RECEIVED));
        eventStore.save(new Event("t2", Map.of(), Instant.now(), "modL", EventStatus.DELIVERED));

        ResponseEntity<?> resp = controller.listEvents("Bearer listall", null);
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(List.class, resp.getBody());
        assertEquals(2, ((List<?>) resp.getBody()).size());
    }

    @Test
    void acknowledgeEvent_putMethod_worksSameAsPost() {
        String eventId = java.util.UUID.randomUUID().toString();
        ((TestTokenService) tokenService).setResponse("Bearer ack-put", "modP");
        when(moduleRegistry.isAuthorizedModule("modP")).thenReturn(true);
        when(originMapper.map("modP")).thenReturn("Inventario");
        com.example.kafkamiddleware.persistence.EventAckEntity ent = createMockAckEntity(eventId, "Inventario");
        AckService.AckResult result = new AckService.AckResult(ent, true);
        when(ackService.ack(any(), any(), any())).thenReturn(result);

        ResponseEntity<?> resp = controller.acknowledgeEventPut("Bearer ack-put", eventId, null);
        assertEquals(201, resp.getStatusCode().value());
        verify(coreApiClient, times(1)).forwardAckToCore(any());
    }

    @Test
    void acknowledgeEvent_withConsumedAt_usesProvidedTimestamp() {
        String eventId = java.util.UUID.randomUUID().toString();
        Instant consumedAt = Instant.now().minusSeconds(10);
        ((TestTokenService) tokenService).setResponse("Bearer ack-time", "modA");
        when(moduleRegistry.isAuthorizedModule("modA")).thenReturn(true);
        when(originMapper.map("modA")).thenReturn("Ventas");
        com.example.kafkamiddleware.persistence.EventAckEntity ent = createMockAckEntity(eventId, "Ventas");
        AckService.AckResult result = new AckService.AckResult(ent, true);
        when(ackService.ack(eq(eventId), eq("Ventas"), eq(consumedAt))).thenReturn(result);

        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer ack-time", eventId, Map.of("consumedAt", consumedAt.toString()));
        assertEquals(201, resp.getStatusCode().value());
        verify(ackService).ack(eventId, "Ventas", consumedAt);
    }

    @Test
    void acknowledgeEvent_coreForwardFails_stillReturnsSuccess() {
        String eventId = java.util.UUID.randomUUID().toString();
        ((TestTokenService) tokenService).setResponse("Bearer ack-fail", "modF");
        when(moduleRegistry.isAuthorizedModule("modF")).thenReturn(true);
        when(originMapper.map("modF")).thenReturn("Analitica");
        com.example.kafkamiddleware.persistence.EventAckEntity ent = createMockAckEntity(eventId, "Analitica");
        AckService.AckResult result = new AckService.AckResult(ent, true);
        when(ackService.ack(any(), any(), any())).thenReturn(result);
        doThrow(new RuntimeException("Core is down")).when(coreApiClient).forwardAckToCore(any());

        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer ack-fail", eventId, null);
        assertEquals(201, resp.getStatusCode().value()); // Should still succeed
        verify(coreApiClient, times(1)).forwardAckToCore(any()); // Verify it was called
    }

    @Test
    void receiveEvent_nullPayload_createsEmptyMap() {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "evt-null-payload");
        body.put("payload", null);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer null-pl", "modN");
        when(moduleRegistry.isAuthorizedModule("modN")).thenReturn(true);

        ResponseEntity<?> resp = controller.receiveEvent("Bearer null-pl", body);
        assertEquals(202, resp.getStatusCode().value());
        String id = (String) ((Map<?,?>) resp.getBody()).get("eventId");
        Event found = eventStore.findById(id);
        assertNotNull(found);
        assertTrue(found.getPayload().isEmpty());
    }

    @Test
    void receiveEvent_missingType_returnsBadRequest() {
        Map<String, Object> body = Map.of("payload", Map.of("a", 1));
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of()); // Assume schema validation passes for this test
        ((TestTokenService) tokenService).setResponse("Bearer no-type", "modNT");
        when(moduleRegistry.isAuthorizedModule("modNT")).thenReturn(true);

        ResponseEntity<?> resp = controller.receiveEvent("Bearer no-type", body);
        assertEquals(400, resp.getStatusCode().value());
        Map<?,?> respBody = (Map<?,?>) resp.getBody();
        assertEquals("type is required", respBody.get("error"));
    }

    @Test
    void receiveEvent_invalidTimestampString_generatesNewTimestamp() {
        Map<String, Object> body = Map.of("type", "evt-bad-ts", "timestamp", "not-a-date");
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        ((TestTokenService) tokenService).setResponse("Bearer bad-ts", "modBT");
        when(moduleRegistry.isAuthorizedModule("modBT")).thenReturn(true);

        // Freeze time for consistent test results
        Instant before = Instant.now();
        ResponseEntity<?> resp = controller.receiveEvent("Bearer bad-ts", body);
        Instant after = Instant.now();

        assertEquals(202, resp.getStatusCode().value());
        String id = (String) ((Map<?,?>) resp.getBody()).get("eventId");
        Event found = eventStore.findById(id);
        assertNotNull(found);
        assertTrue(found.getTimestamp().isAfter(before.minusMillis(1)) && found.getTimestamp().isBefore(after.plusMillis(1)));
    }

    @Test
    void acknowledgeEvent_invalidConsumedAt_callsAckWithNullTimestamp() {
        String eventId = java.util.UUID.randomUUID().toString();
        ((TestTokenService) tokenService).setResponse("Bearer ack-bad-time", "modA");
        when(moduleRegistry.isAuthorizedModule("modA")).thenReturn(true);
        when(originMapper.map("modA")).thenReturn("Ventas");
        com.example.kafkamiddleware.persistence.EventAckEntity ent = createMockAckEntity(eventId, "Ventas");
        AckService.AckResult result = new AckService.AckResult(ent, true);
        // Expect ack to be called with null for the timestamp
        when(ackService.ack(eq(eventId), eq("Ventas"), isNull())).thenReturn(result);

        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer ack-bad-time", eventId, Map.of("consumedAt", "not-a-valid-date"));

        assertEquals(201, resp.getStatusCode().value());
        // Verify that ack was called with a null timestamp
        verify(ackService).ack(eventId, "Ventas", null);
    }

}
