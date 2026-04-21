package org.ysu.ckqaback.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseUtilsTest {

    @Test
    void shouldBuildSuccessResponseWithStandardBusinessCode() {
        ApiResponse<String> response = ApiResponseUtils.success("ok");

        assertThat(response.getCode()).isEqualTo(ApiResultCode.SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo(ApiResultCode.SUCCESS.getMessage());
        assertThat(response.getData()).isEqualTo("ok");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void shouldBuildErrorResponseFromResultCode() {
        ApiResponse<Void> response = ApiResponseUtils.error(ApiResultCode.VALIDATION_ERROR);

        assertThat(response.getCode()).isEqualTo(ApiResultCode.VALIDATION_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo(ApiResultCode.VALIDATION_ERROR.getMessage());
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }
}
