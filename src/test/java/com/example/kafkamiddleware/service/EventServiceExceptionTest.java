package com.example.kafkamiddleware.service;

import com.example.kafkamiddleware.dto.EventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventServiceExceptionTest {

//        @Test
//        void sendToCore_whenKafkaTemplateSendThrows_throwsEventSendException() throws Exception {
//            @SuppressWarnings("unchecked")
//            KafkaTemplate<String,String> kt = mock(KafkaTemplate.class);
//            doThrow(new RuntimeException("kafka down")).when(kt).send(anyString(), anyString(), anyString());
//
//            ObjectProvider<KafkaTemplate<String,String>> prov = mock(ObjectProvider.class);
//            when(prov.getIfAvailable()).thenReturn(kt);
//            ObjectMapper mapper = new ObjectMapper();
//            mapper.registerModule(new JavaTimeModule());
//            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//            ModuleMessageStore store = new ModuleMessageStore();
//            EventService svc = new EventService(prov, mapper, store);
//
//            java.lang.reflect.Field f = EventService.class.getDeclaredField("kafkaEnabled");
//            f.setAccessible(true);
//            f.set(svc, true);
//
//            EventDto dto = new EventDto("t", Map.of("x",1), Instant.now(), "modX");
//            Exception ex = assertThrows(EventService.EventSendException.class, () -> svc.sendToCore(dto));
//            assertNotNull(ex.getCause());
//            assertTrue(ex.getCause().getMessage().contains("kafka down"));
//        }
}
