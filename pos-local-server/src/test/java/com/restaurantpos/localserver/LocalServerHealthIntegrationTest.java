package com.restaurantpos.localserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LocalServerHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUpAndPropagatesCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/system/health").header("X-Correlation-Id", "corr-test-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-test-123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serviceName").value("pos-local-server"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void protectedEndpointRejectsUnauthenticatedRequestsWithSanitizedJson() throws Exception {
        mockMvc.perform(get("/api/v1/protected-resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }
}
