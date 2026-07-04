package io.github.pratikpanchal22.authserver.controller;

import io.github.pratikpanchal22.authserver.service.HrdService;
import io.github.pratikpanchal22.authserver.service.HrdService.HrdResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HrdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HrdService hrdService;

    @Test
    void lookup_federatedDomain_returnsFederatedMethod() throws Exception {
        when(hrdService.lookup("alice@gmail.com")).thenReturn(HrdResult.federated("google"));

        mockMvc.perform(get("/hrd/lookup").param("email", "alice@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("FEDERATED"))
                .andExpect(jsonPath("$.registrationId").value("google"));
    }

    @Test
    void lookup_localDomain_returnsLocalMethod() throws Exception {
        when(hrdService.lookup("bob@example.com")).thenReturn(HrdResult.local());

        mockMvc.perform(get("/hrd/lookup").param("email", "bob@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("LOCAL"))
                .andExpect(jsonPath("$.registrationId").doesNotExist());
    }

    @Test
    void lookup_noEmailParam_returns400() throws Exception {
        mockMvc.perform(get("/hrd/lookup"))
                .andExpect(status().isBadRequest());
    }
}
