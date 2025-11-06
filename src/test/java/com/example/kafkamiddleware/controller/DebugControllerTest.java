package com.example.kafkamiddleware.controller;

import com.example.kafkamiddleware.persistence.EventRepository;
import com.example.kafkamiddleware.service.EventStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class DebugControllerTest {

    @Autowired
    private EventStore eventStore;

    @MockBean
    private EventRepository repository;

    @Nested
    @TestPropertySource(properties = "app.storage.type=MEMORY")
    class MemoryMode {
        @Autowired
        private DebugController debugController;

        @Test
        void stats_reportsMemoryModeWhenForcedByProperty() {
            Map<String, Object> stats = debugController.stats();
            assertEquals("MEMORY", stats.get("effectiveMode"));
            assertEquals("MEMORY", String.valueOf(stats.get("storageTypeProperty")).toUpperCase());
            assertTrue((Boolean) stats.get("repositoryAvailable"));
            assertTrue(((Number) stats.get("dbCount")).longValue() >= -1L);
            assertTrue(((Number) stats.get("totalCount")).intValue() >= 0);
        }
    }

    @Nested
    @TestPropertySource(properties = "app.storage.type=DATABASE")
    class DatabaseMode {
        @Autowired
        private DebugController debugController;

        @Test
        void stats_reportsDatabaseModeWhenRepoPresentAndNotForced() {
            when(repository.count()).thenReturn(0L);
            Map<String, Object> stats = debugController.stats();
            assertEquals("DATABASE", stats.get("effectiveMode"));
            assertEquals(0L, ((Number) stats.get("dbCount")).longValue());
        }
    }

    @Test
    void stats_handlesNullRepository() {
        DebugController controller = new DebugController(eventStore, null);
        try {
            java.lang.reflect.Field f = DebugController.class.getDeclaredField("storageTypeProp");
            f.setAccessible(true);
            f.set(controller, "DATABASE"); // Simulate DB mode but with no repo
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> stats = controller.stats();
        assertEquals("MEMORY", stats.get("effectiveMode"));
        assertFalse((Boolean) stats.get("repositoryAvailable"));
        assertEquals(-1L, ((Number) stats.get("dbCount")).longValue());
    }
}
