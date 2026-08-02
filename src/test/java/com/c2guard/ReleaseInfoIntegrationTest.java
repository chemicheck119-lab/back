package com.c2guard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,info",
        "management.info.env.enabled=true",
        "info.application.name=c2guard",
        "info.application.version=0.0.1-SNAPSHOT",
        "info.release.gitCommit=0123456789abcdef0123456789abcdef01234567",
        "info.release.environment=test"
})
@AutoConfigureMockMvc
class ReleaseInfoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOnlyNonSecretApplicationAndReleaseMetadata() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.name").value("c2guard"))
                .andExpect(jsonPath("$.application.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.release.gitCommit")
                        .value("0123456789abcdef0123456789abcdef01234567"))
                .andExpect(jsonPath("$.release.environment").value("test"))
                .andExpect(jsonPath("$.chemicheck119").doesNotExist());
    }
}
