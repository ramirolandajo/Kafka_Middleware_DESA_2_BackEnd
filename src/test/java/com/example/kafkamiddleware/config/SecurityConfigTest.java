package com.example.kafkamiddleware.config;

import com.example.kafkamiddleware.controller.HelloController;
import com.example.kafkamiddleware.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({SecurityConfig.class, HelloController.class, SecurityConfigTest.DummySecuredController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @RestController
    static class DummySecuredController {
        @GetMapping("/private/area")
        public String secured() { return "secret"; }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/hello", "/_debug/stats"})
    void publicEndpoints_areAccessibleWithoutAuth(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void securedEndpoints_areAccessibleWithAuth() throws Exception {
        when(tokenService.validateAndExtractClientId(anyString())).thenReturn("mockClientId");
        mockMvc.perform(get("/events").header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEndpoint_requiresAuth() throws Exception {
        mockMvc.perform(get("/private/area"))
                .andExpect(status().isForbidden());
    }
}
