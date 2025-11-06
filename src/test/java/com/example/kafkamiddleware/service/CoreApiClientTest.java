package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.Event;
import com.example.kafkamiddleware.dto.EventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CoreApiClientTest {

    private EventStore eventStore;
    private CoreApiClient client;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStore.class);
        client = new CoreApiClient(eventStore);
    }

    @Test
    void forward_whenForwardDisabled_doesNotCallRemote() {
        try {
            java.lang.reflect.Field f = CoreApiClient.class.getDeclaredField("forwardEnabled");
            f.setAccessible(true);
            f.set(client, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Event e = new Event("t", Map.of("k","v"), Instant.now(), "mod", EventStatus.RECEIVED);
        client.forward(e, "Ventas");
    }

    @Test
    void forward_whenRemoteOk_updatesEventStatusToDelivered() {
        RestTemplate rt = mock(RestTemplate.class);
        Event e = new Event("t", Map.of("k","v"), Instant.now(), "mod", EventStatus.RECEIVED);

        client = new CoreApiClient(eventStore);
        try {
            java.lang.reflect.Field restField = CoreApiClient.class.getDeclaredField("restTemplate");
            restField.setAccessible(true);
            restField.set(client, rt);

            java.lang.reflect.Field f = CoreApiClient.class.getDeclaredField("forwardEnabled");
            f.setAccessible(true);
            f.set(client, true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        ResponseEntity<String> resp = ResponseEntity.accepted().body("ok");
        when(rt.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class))).thenReturn(resp);

        client.forward(e, "Ventas");

        verify(eventStore, times(1)).updateStatus(e.getId(), EventStatus.DELIVERED);
    }

    @Test
    void forward_whenRemoteError_doesNotUpdateStatus() {
        RestTemplate rt = mock(RestTemplate.class);
        Event e = new Event("t", Map.of("k","v"), Instant.now(), "mod", EventStatus.RECEIVED);

        client = new CoreApiClient(eventStore);
        try {
            java.lang.reflect.Field restField = CoreApiClient.class.getDeclaredField("restTemplate");
            restField.setAccessible(true);
            restField.set(client, rt);

            java.lang.reflect.Field f = CoreApiClient.class.getDeclaredField("forwardEnabled");
            f.setAccessible(true);
            f.set(client, true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        ResponseEntity<String> resp = ResponseEntity.status(500).body("err");
        when(rt.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class))).thenReturn(resp);

        client.forward(e, "Ventas");

        verify(eventStore, never()).updateStatus(anyString(), any());
    }

    @Test
    void forwardAckToCore_whenEnabled_callsRemote() {
        RestTemplate rt = mock(RestTemplate.class);
        client = new CoreApiClient(eventStore);
        try {
            java.lang.reflect.Field restField = CoreApiClient.class.getDeclaredField("restTemplate");
            restField.setAccessible(true);
            restField.set(client, rt);

            java.lang.reflect.Field f = CoreApiClient.class.getDeclaredField("forwardEnabled");
            f.setAccessible(true);
            f.set(client, true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        when(rt.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        client.forwardAckToCore(Map.of("eventId", "x"));

        verify(rt, times(1)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void forwardAsync_exceptionInForward_isCaught() {
        CoreApiClient clientSpy = spy(client);
        Event e = new Event("t", Map.of(), Instant.now(), "mod", EventStatus.RECEIVED);
        doThrow(new RuntimeException("test exception")).when(clientSpy).forward(any(), any());

        // No exception should be thrown
        clientSpy.forwardAsync(e, "Ventas");

        verify(clientSpy, times(1)).forward(e, "Ventas");
    }

    @Test
    void buildUrl_handlesTrailingSlashes() throws Exception {
        CoreApiClient localClient = new CoreApiClient(eventStore);
        java.lang.reflect.Field baseUrlField = CoreApiClient.class.getDeclaredField("coreApiBase");
        baseUrlField.setAccessible(true);
        java.lang.reflect.Field pathField = CoreApiClient.class.getDeclaredField("coreApiPath");
        pathField.setAccessible(true);

        baseUrlField.set(localClient, "http://localhost:8080/");
        pathField.set(localClient, "/api/events");

        java.lang.reflect.Method method = CoreApiClient.class.getDeclaredMethod("buildUrl");
        method.setAccessible(true);
        String url = (String) method.invoke(localClient);

        assertEquals("http://localhost:8080/api/events", url);
    }
}
