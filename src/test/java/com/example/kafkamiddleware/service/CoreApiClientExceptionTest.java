package com.example.kafkamiddleware.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.*;

class CoreApiClientExceptionTest {

    private EventStore eventStore;
    private CoreApiClient client;

    @BeforeEach
    void setUp() {
        eventStore = mock(EventStore.class);
        client = new CoreApiClient(eventStore);
    }

    @Test
    void forward_whenRestTemplateThrows_doesNotUpdateStatus() throws Exception {
        RestTemplate rt = mock(RestTemplate.class);
        java.lang.reflect.Field restField = CoreApiClient.class.getDeclaredField("restTemplate");
        restField.setAccessible(true);
        restField.set(client, rt);

        java.lang.reflect.Field f = CoreApiClient.class.getDeclaredField("forwardEnabled");
        f.setAccessible(true);
        f.set(client, true);

        // simulate RestTemplate throwing exception
        when(rt.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class))).thenThrow(new RestClientException("conn fail"));

        com.example.kafkamiddleware.dto.Event e = new com.example.kafkamiddleware.dto.Event("t", Map.of("k","v"), Instant.now(), "mod", com.example.kafkamiddleware.dto.EventStatus.RECEIVED);
        client.forward(e, "Ventas");

        // should not call updateStatus
        verify(eventStore, never()).updateStatus(anyString(), any());
    }
}

