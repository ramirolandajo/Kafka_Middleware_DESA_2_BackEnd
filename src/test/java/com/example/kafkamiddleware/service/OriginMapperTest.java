package com.example.kafkamiddleware.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OriginMapperTest {

    @Test
    void map_whenKnownKey_returnsMapped() {
        OriginMapper m = new OriginMapper();
        // Simular propiedad privada
        try {
            java.lang.reflect.Field f = OriginMapper.class.getDeclaredField("mappingProperty");
            f.setAccessible(true);
            f.set(m, "foo:Ventas,bar:Inventario");
            m.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals("Ventas", m.map("foo"));
        assertEquals("Inventario", m.map("bar"));
        assertEquals("unknown", m.map("unknown"));
    }

    @Test
    void map_whenPropertyNotSet_returnsInput() {
        OriginMapper m = new OriginMapper();
        try {
            java.lang.reflect.Field f = OriginMapper.class.getDeclaredField("mappingProperty");
            f.setAccessible(true);
            f.set(m, ""); // Empty property
            m.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals("any-client", m.map("any-client"));
    }

    @Test
    void map_handlesMalformedPairs() {
        OriginMapper m = new OriginMapper();
        try {
            java.lang.reflect.Field f = OriginMapper.class.getDeclaredField("mappingProperty");
            f.setAccessible(true);
            f.set(m, "good:Ventas,bad-format, another:Inventario");
            m.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals("Ventas", m.map("good"));
        assertEquals("Inventario", m.map("another"));
        assertEquals("bad-format", m.map("bad-format")); // Should not be mapped
    }
}
