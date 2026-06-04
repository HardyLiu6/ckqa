package org.ysu.ckqaback.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NullTypeSafetySourceTest {

    private static final List<Pattern> NULL_UNSAFE_METHOD_REFERENCES = List.of(
            Pattern.compile("\\b(?:Long|Integer|Double)::sum\\b"),
            Pattern.compile("Comparator\\.nulls(?:First|Last)\\([^)]*::compareTo\\)")
    );

    @Test
    void mainSourcesAvoidNullUnsafeMethodReferencesInBoxedFunctionalDescriptors() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectViolations(path, violations));
        }

        assertThat(violations)
                .as("避免 boxed 函数式接口通过方法引用隐式拆箱或绕过 null-aware comparator")
                .isEmpty();
    }

    private void collectViolations(Path path, List<String> violations) {
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException("读取源码失败: " + path, error);
        }
        for (Pattern pattern : NULL_UNSAFE_METHOD_REFERENCES) {
            if (pattern.matcher(source).find()) {
                violations.add(path.toString() + " matches " + pattern.pattern());
            }
        }
    }
}
