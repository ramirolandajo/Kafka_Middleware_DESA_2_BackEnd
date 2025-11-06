package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.EventDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.NonNull;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "app.storage.type=DATABASE")
@SpringBootTest
class EventServiceTest {

    // helper to create ObjectMapper with Java time module
    private ObjectMapper newMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    static class TestObjectProvider<T> implements ObjectProvider<T> {
        private final T instance;
        public TestObjectProvider(T instance) { this.instance = instance; }
        @Override
        @NonNull
        public T getObject(@NonNull Object... args) { return instance; }

        @Override
        @NonNull
        public T getIfAvailable() { return instance; }

        @Override
        @NonNull
        public T getIfUnique() { return instance; }

        // ObjectFactory requires no-arg getObject()
        @Override
        @NonNull
        public T getObject() { return instance; }

        @Override
        @NonNull
        public Stream<T> orderedStream() { return Stream.of(instance); }
    }

    @Test
    void sendToCore_whenKafkaDisabled_shouldStoreInModuleMessageStore() throws Exception {
        ObjectProvider<KafkaTemplate<String,String>> prov = new TestObjectProvider<>(null);
        ObjectMapper mapper = newMapper();
        ModuleMessageStore store = new ModuleMessageStore();
        EventService svc = new EventService(prov, mapper, store);

        // Force kafka disabled using reflection on private field
        java.lang.reflect.Field f = EventService.class.getDeclaredField("kafkaEnabled");
        f.setAccessible(true);
        f.set(svc, false);

        EventDto dto = new EventDto("t", Map.of("x",1), Instant.now(), "mod1");
        svc.sendToCore(dto);

        var polled = store.pollMessagesForModule("mod1");
        assertEquals(1, polled.size());
        assertEquals("t", polled.get(0).getType());
    }

    @Test
    void sendToCore_whenKafkaEnabled_butNoTemplate_shouldThrow() throws Exception {
        ObjectProvider<KafkaTemplate<String,String>> prov = new TestObjectProvider<>(null);
        ObjectMapper mapper = newMapper();
        ModuleMessageStore store = new ModuleMessageStore();
        EventService svc = new EventService(prov, mapper, store);

        java.lang.reflect.Field f = EventService.class.getDeclaredField("kafkaEnabled");
        f.setAccessible(true);
        f.set(svc, true);

        EventDto dto = new EventDto("t", Map.of("x",1), Instant.now(), "mod1");
        Exception ex = assertThrows(EventService.EventSendException.class, () -> svc.sendToCore(dto));
        assertTrue(ex.getMessage().contains("Kafka is enabled but KafkaTemplate bean is missing"));
    }

    @Test
    void sendToCore_serializationError_throwsEventSendException() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String,String> kt = mock(KafkaTemplate.class);
        ObjectProvider<KafkaTemplate<String,String>> prov = new TestObjectProvider<>(kt);
        ObjectMapper mapper = mock(ObjectMapper.class);
        ModuleMessageStore store = new ModuleMessageStore();
        EventService svc = new EventService(prov, mapper, store);

        java.lang.reflect.Field f = EventService.class.getDeclaredField("kafkaEnabled");
        f.setAccessible(true);
        f.set(svc, true);

        EventDto dto = new EventDto("t", Map.of(), Instant.now(), "m");
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("test error") {});

        EventService.EventSendException ex = assertThrows(EventService.EventSendException.class, () -> svc.sendToCore(dto));
        assertEquals("Failed to serialize event", ex.getMessage());
        assertNotNull(ex.getCause());
        assertInstanceOf(JsonProcessingException.class, ex.getCause());
    }

//    @Test
//    void sendToCore_kafkaSendError_throwsEventSendException() throws Exception {
//        @SuppressWarnings("unchecked")
//        KafkaTemplate<String,String> kt = mock(KafkaTemplate.class);
//        ObjectProvider<KafkaTemplate<String,String>> prov = new TestObjectProvider<>(kt);
//        ObjectMapper mapper = newMapper();
//        ModuleMessageStore store = new ModuleMessageStore();
//        EventService svc = new EventService(prov, mapper, store);
//
//        java.lang.reflect.Field f = EventService.class.getDeclaredField("kafkaEnabled");
//        f.setAccessible(true);
//        f.set(svc, true);
//
//        EventDto dto = new EventDto("t", Map.of(), Instant.now(), "m");
//        // Simulate a failed future from Kafka
//        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> future = new CompletableFuture<>();
//        future.completeExceptionally(new RuntimeException("Kafka down"));
//        when(kt.send(anyString(), anyString(), anyString())).thenReturn(future);
//
//        EventService.EventSendException ex = assertThrows(EventService.EventSendException.class, () -> svc.sendToCore(dto));
//        assertTrue(ex.getMessage().startsWith("Failed to send event"));
//        assertNotNull(ex.getCause());
//        assertInstanceOf(RuntimeException.class, ex.getCause().getCause());
//    }
}
