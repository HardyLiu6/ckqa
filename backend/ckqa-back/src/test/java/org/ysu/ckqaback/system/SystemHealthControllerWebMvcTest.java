package org.ysu.ckqaback.system;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.system.dto.SystemHealthItemResponse;
import org.ysu.ckqaback.system.dto.SystemHealthResponse;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemHealthControllerWebMvcTest {

    private SystemHealthService systemHealthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        systemHealthService = Mockito.mock(SystemHealthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SystemHealthController(systemHealthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnDetailedHealthStatus() throws Exception {
        SystemHealthResponse response = SystemHealthResponse.up(
                SystemHealthItemResponse.of("mysql", true, true, "ok"),
                SystemHealthItemResponse.of("graphrag-api", true, false, "v1/models unavailable")
        );
        given(systemHealthService.check()).willReturn(response);

        mockMvc.perform(get(ApiPaths.SYSTEM_HEALTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("mysql"))
                .andExpect(jsonPath("$.data.items[1].ready").value(false));
    }
}
