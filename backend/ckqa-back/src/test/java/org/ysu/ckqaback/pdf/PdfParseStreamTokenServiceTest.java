package org.ysu.ckqaback.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.JwtProperties;
import org.ysu.ckqaback.exception.BusinessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfParseStreamTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-07T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldIssueShortLivedTokenBoundToMaterialId() {
        PdfParseStreamTokenService service = newService(FIXED_CLOCK, Duration.ofMinutes(5));

        ParseStreamTokenResponse response = service.issue(7L);

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getExpiresAt()).isEqualTo(Instant.parse("2026-05-07T08:05:00Z"));
        assertThat(service.validate(7L, response.getToken()).materialId()).isEqualTo(7L);
    }

    @Test
    void shouldRejectTokenForDifferentMaterial() {
        PdfParseStreamTokenService service = newService(FIXED_CLOCK, Duration.ofMinutes(5));
        String token = service.issue(7L).getToken();

        assertThatThrownBy(() -> service.validate(8L, token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("解析进度令牌无效");
    }

    @Test
    void shouldRejectExpiredToken() {
        PdfParseStreamTokenService issuer = newService(FIXED_CLOCK, Duration.ofMinutes(5));
        String token = issuer.issue(7L).getToken();
        PdfParseStreamTokenService verifier = newService(
                Clock.fixed(Instant.parse("2026-05-07T08:06:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5)
        );

        assertThatThrownBy(() -> verifier.validate(7L, token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("解析进度令牌已过期");
    }

    private static PdfParseStreamTokenService newService(Clock clock, Duration ttl) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("ckqa-test-stream-token-secret-at-least-32-chars");
        return new PdfParseStreamTokenService(properties, new ObjectMapper(), clock, ttl);
    }
}
