package org.ysu.ckqaback.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.system.SystemHealthController;
import org.ysu.ckqaback.system.SystemHealthService;
import org.ysu.ckqaback.system.dto.SystemHealthResponse;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebCorsConfigTest {

    @Test
    void shouldAllowLocalFrontendOrigin() throws Exception {
        MockMvc mockMvc = buildMockMvc(defaultProperties());

        mockMvc.perform(options(ApiPaths.SYSTEM_HEALTH)
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
    }

    @Test
    void shouldRejectNonWhitelistedOrigin() throws Exception {
        MockMvc mockMvc = buildMockMvc(defaultProperties());

        mockMvc.perform(options(ApiPaths.SYSTEM_HEALTH)
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldDisableCorsRegistrationWhenConfiguredOff() throws Exception {
        CkqaCorsProperties properties = defaultProperties();
        properties.setEnabled(false);
        FilterRegistrationBean<?> registration = new WebCorsConfig(properties).corsFilterRegistration();

        MockMvc mockMvc = buildMockMvcWithoutCors().build();

        mockMvc.perform(get(ApiPaths.SYSTEM_HEALTH)
                        .header("Origin", "http://127.0.0.1:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        org.assertj.core.api.Assertions.assertThat(registration.isEnabled()).isFalse();
    }

    private MockMvc buildMockMvc(CkqaCorsProperties properties) {
        FilterRegistrationBean<?> registration = new WebCorsConfig(properties).corsFilterRegistration();
        return buildMockMvcWithoutCors()
                .addFilters(registration.getFilter())
                .build();
    }

    private StandaloneMockMvcBuilder buildMockMvcWithoutCors() {
        SystemHealthService service = mock(SystemHealthService.class);
        when(service.check()).thenReturn(new SystemHealthResponse(
                true,
                List.of()
        ));

        return MockMvcBuilders.standaloneSetup(new SystemHealthController(service));
    }

    private CkqaCorsProperties defaultProperties() {
        return new CkqaCorsProperties();
    }
}
