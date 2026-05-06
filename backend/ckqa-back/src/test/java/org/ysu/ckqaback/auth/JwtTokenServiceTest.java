package org.ysu.ckqaback.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void shouldIssueAndParseSignedToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-that-is-long-enough-for-hmac");
        properties.setIssuer("ckqa-test");
        properties.setTtl(Duration.ofMinutes(30));
        JwtTokenService service = new JwtTokenService(properties);
        AuthenticatedUser user = new AuthenticatedUser(
                1L,
                "ADM2026001",
                "admin.heqh",
                "何启航",
                List.of("admin"),
                List.of("*")
        );

        JwtTokenService.IssuedToken token = service.issue(user);
        AuthenticatedUser parsed = service.parse(token.accessToken());

        assertThat(token.accessToken()).contains(".");
        assertThat(token.expiresAt()).isAfter(token.issuedAt());
        assertThat(parsed.userCode()).isEqualTo("ADM2026001");
        assertThat(parsed.roles()).containsExactly("admin");
        assertThat(parsed.permissions()).containsExactly("*");
    }
}
