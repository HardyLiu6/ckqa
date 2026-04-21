package org.ysu.ckqaback.codegen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusCodeGeneratorTest {

    @Test
    void shouldUseAllTablesByDefault() {
        MybatisPlusCodeGenerator.GeneratorOptions options =
                MybatisPlusCodeGenerator.GeneratorOptions.parse(new String[0]);

        assertThat(options.tables()).contains("courses", "qa_messages", "authorization_audit_logs");
        assertThat(options.overwrite()).isFalse();
    }

    @Test
    void shouldParseCustomTablesAndOverwriteFlag() {
        MybatisPlusCodeGenerator.GeneratorOptions options =
                MybatisPlusCodeGenerator.GeneratorOptions.parse(
                        new String[]{"--tables=users,roles", "--overwrite=true"});

        assertThat(options.tables()).containsExactly("users", "roles");
        assertThat(options.overwrite()).isTrue();
    }
}
