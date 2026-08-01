package com.c2guard.auth.staging;

import com.c2guard.integration.model.ModelApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StagingAuthDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void doesNotExposeTheStagingLoginRouteByDefault() throws Exception {
        mockMvc.perform(get("/auth/staging/login"))
                .andExpect(status().isNotFound());
    }
}
