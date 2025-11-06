package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventControllerAdditionalTest {

    static class TestTokenService extends TokenService {
        private final java.util.Map<String,Object> map = new java.util.HashMap<>();
        public void setResponse(String b, String c) { map.put(b,c); }
        @Override public String validateAndExtractClientId(String bearerToken) throws TokenValidationException { if (map.containsKey(bearerToken)) return (String)map.get(bearerToken); throw new TokenValidationException("no token"); }
    }

    private TestTokenService tokenService;
    private ModuleRegistry moduleRegistry;
    private EventValidator eventValidator;
    private EventStore eventStore;
    private ModuleMessageStore moduleMessageStore;
    private CoreApiClient coreApiClient;
    private OriginMapper originMapper;
    // ackService used only when rebuilding controller in specific tests -> keep local in tests
    private EventController controller;

    @BeforeEach
    void setUp() throws Exception {
        tokenService = new TestTokenService();
        moduleRegistry = mock(ModuleRegistry.class);
        eventValidator = mock(EventValidator.class);
        eventStore = new EventStore();
        java.lang.reflect.Field f = EventStore.class.getDeclaredField("storageType"); f.setAccessible(true); f.set(eventStore, "MEMORY");
        moduleMessageStore = new ModuleMessageStore();
        coreApiClient = mock(CoreApiClient.class);
        originMapper = mock(OriginMapper.class);
        AckService ackService = mock(AckService.class);
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, moduleMessageStore, new ObjectMapper(), coreApiClient, originMapper, ackService);
    }

    @Test
    void receiveEvent_validatorReturnsNull_isAccepted() {
        tokenService.setResponse("Bearer vnull", "modV");
        when(moduleRegistry.isAuthorizedModule("modV")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(null);
        when(originMapper.map("modV")).thenReturn("Ventas");

        Map<String,Object> body = Map.of("type","t", "payload", Map.of("x",1));
        ResponseEntity<?> resp = controller.receiveEvent("Bearer vnull", body);
        assertEquals(202, resp.getStatusCode().value());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?,?> rb = (Map<?,?>) resp.getBody();
        assertNotNull(rb.get("eventId"));
    }

    @Test
    void receiveEvent_originMapperReturnsNull_forwardsWithNullCanonicalOrigin() {
        tokenService.setResponse("Bearer onull", "modO");
        when(moduleRegistry.isAuthorizedModule("modO")).thenReturn(true);
        when(eventValidator.validate(any())).thenReturn(java.util.Set.of());
        when(originMapper.map("modO")).thenReturn(null);

        Map<String,Object> body = Map.of("type","t");
        ResponseEntity<?> resp = controller.receiveEvent("Bearer onull", body);
        assertEquals(202, resp.getStatusCode().value());

        ArgumentCaptor<Event> capEv = ArgumentCaptor.forClass(Event.class);
        ArgumentCaptor<String> capOrigin = ArgumentCaptor.forClass(String.class);
        verify(coreApiClient).forwardAsync(capEv.capture(), capOrigin.capture());
        assertNull(capOrigin.getValue());
    }

    @Test
    void pollEvents_authorized_butNoMessages_returnsEmptyList() {
        tokenService.setResponse("Bearer pem", "modP");
        when(moduleRegistry.isAuthorizedModule("modP")).thenReturn(true);

        // ensure no messages
        var resp = controller.pollEvents("Bearer pem");
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(List.class, resp.getBody());
        List<?> list = (List<?>) resp.getBody();
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void acknowledgeEvent_consumedAt_invalidString_passesNullToAckService() {
        String eventId = java.util.UUID.randomUUID().toString();
        tokenService.setResponse("Bearer badts", "modA");
        when(moduleRegistry.isAuthorizedModule("modA")).thenReturn(true);
        when(originMapper.map("modA")).thenReturn("Ventas");

        AckService ackMock = mock(AckService.class);
        com.example.kafkamiddleware.persistence.EventAckEntity ackEnt = new com.example.kafkamiddleware.persistence.EventAckEntity();
        ackEnt.setEventId(eventId);
        ackEnt.setConsumer("Ventas");
        ackEnt.setStatus("CONSUMED");
        ackEnt.setAttempts(1);
        ackEnt.setFirstSeenAt(Instant.now());
        ackEnt.setLastSeenAt(Instant.now());
        when(ackMock.ack(eq(eventId), eq("Ventas"), any())).thenReturn(new AckService.AckResult(ackEnt, true));
        // rebuild controller with our ack mock
        controller = new EventController(tokenService, moduleRegistry, eventValidator, eventStore, moduleMessageStore, new ObjectMapper(), coreApiClient, originMapper, ackMock);

        Map<String,Object> body = Map.of("consumedAt", "not-a-time");
        ResponseEntity<?> resp = controller.acknowledgeEvent("Bearer badts", eventId, body);
        assertTrue(resp.getStatusCode().is2xxSuccessful());

        ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
        verify(ackMock).ack(eq(eventId), eq("Ventas"), cap.capture());
        // parsed consumedAt was invalid, so controller passes null
        assertNull(cap.getValue());
    }
}
