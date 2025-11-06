package com.example.kafkamiddleware.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

class CoreApiClientAckDisabledTest {

    @Test
    void forwardAckToCore_whenDisabled_doesNotCallRestTemplate() throws Exception {
        EventStore store = mock(EventStore.class);
        CoreApiClient client = new CoreApiClient(store);

        java.lang.reflect.Field f = CoreApiClient.class.getDeclaredField("forwardEnabled");
        f.setAccessible(true);
        f.set(client, false);

        // spy on restTemplate
        java.lang.reflect.Field rtField = CoreApiClient.class.getDeclaredField("restTemplate");
        rtField.setAccessible(true);
        Object rt = rtField.get(client);
        Object spy = spy(rt);
        rtField.set(client, spy);

        client.forwardAckToCore(Map.of("eventId", "e1"));

        // restTemplate.exchange should not have been invoked; verify no interactions with spy
        verifyNoInteractions(spy);
    }
}
