package org.ysu.ckqaback.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerWebMvcTest {

    @Test
    void shouldReturnUnifiedValidationError() throws Exception {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DemoController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.data.errors[0].field").value("name"))
                .andExpect(jsonPath("$.data.errors[0].message").value("name不能为空"));
    }

    @Test
    void shouldAvoidJsonBodyWhenSseResponseContentTypeIsAlreadyCommitted() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        var entity = handler.handleException(new IllegalStateException("boom"), response);

        assertThat(entity.getStatusCode().value()).isEqualTo(500);
        assertThat(entity.getBody()).isNull();
    }

    @Test
    void shouldNotLogDisconnectedSseClientAsUnhandledException(CapturedOutput output) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        var entity = handler.handleException(
                new AsyncRequestNotUsableException("Servlet container error notification for disconnected client"),
                response
        );

        assertThat(entity.getStatusCode().value()).isEqualTo(500);
        assertThat(entity.getBody()).isNull();
        assertThat(output).doesNotContain("未处理的接口异常");
    }

    @RestController
    static class DemoController {

        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody DemoRequest request) {
            return "ok";
        }
    }

    record DemoRequest(@NotBlank(message = "name不能为空") String name) {
    }
}
