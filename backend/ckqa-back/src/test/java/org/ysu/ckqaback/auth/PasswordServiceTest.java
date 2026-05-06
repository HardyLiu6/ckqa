package org.ysu.ckqaback.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService service = new PasswordService();

    @Test
    void shouldHashAndVerifyPasswordWithoutStoringPlainText() {
        String hash = service.hash("Ckqa@2026");

        assertThat(hash).startsWith("pbkdf2$");
        assertThat(hash).doesNotContain("Ckqa@2026");
        assertThat(service.matches("Ckqa@2026", hash)).isTrue();
        assertThat(service.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void shouldVerifySeededDemoCredentialHash() {
        String seededHash = "pbkdf2$210000$Y2txYS1qd3QtZGVtby0yNg==$cUUPGgqb4Q6w27xbIWMJ8Wwz9gxGFg2cq7Bb34BJEaU=";

        assertThat(service.matches("Ckqa@2026", seededHash)).isTrue();
        assertThat(service.matches("ckqa@2026", seededHash)).isFalse();
    }
}
