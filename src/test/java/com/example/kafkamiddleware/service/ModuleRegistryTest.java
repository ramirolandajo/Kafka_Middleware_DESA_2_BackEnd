package com.example.kafkamiddleware.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {

    @Test
    void isAuthorizedModule_whenPropertySet_returnsTrueForListedModules() {
        ModuleRegistry r = new ModuleRegistry();
        try {
            java.lang.reflect.Field f = ModuleRegistry.class.getDeclaredField("authorizedModulesProperty");
            f.setAccessible(true);
            f.set(r, "modA,modB  , modC");
            r.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(r.isAuthorizedModule("modA"));
        assertTrue(r.isAuthorizedModule("modB"));
        assertTrue(r.isAuthorizedModule("modC"));
        assertFalse(r.isAuthorizedModule("other"));
    }

    @Test
    void isAuthorizedModule_whenPropertyNotSet_returnsFalse() {
        ModuleRegistry r = new ModuleRegistry();
        try {
            java.lang.reflect.Field f = ModuleRegistry.class.getDeclaredField("authorizedModulesProperty");
            f.setAccessible(true);
            f.set(r, ""); // Empty property
            r.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertFalse(r.isAuthorizedModule("anyModule"));
        assertTrue(r.getAuthorizedModules().isEmpty());
    }

    @Test
    void isAuthorizedModule_handlesExtraWhitespaceAndCommas() {
        ModuleRegistry r = new ModuleRegistry();
        try {
            java.lang.reflect.Field f = ModuleRegistry.class.getDeclaredField("authorizedModulesProperty");
            f.setAccessible(true);
            f.set(r, "  mod1, , mod2,mod3  ,");
            r.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(r.isAuthorizedModule("mod1"));
        assertTrue(r.isAuthorizedModule("mod2"));
        assertTrue(r.isAuthorizedModule("mod3"));
        assertEquals(3, r.getAuthorizedModules().size());
    }
}
