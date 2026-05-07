package org.ysu.ckqaback.integration.process;

import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Python 命令解析器。
 * <p>
 * 本地联调文档里常见的 {@code /path/to/.../python} 是占位符，不应直接交给
 * {@link ProcessBuilder} 执行；未显式配置时优先使用本机常见 conda 环境路径，
 * 找不到再退回到 {@code conda run -n <env> python}。
 * </p>
 */
public final class PythonCommandResolver {

    private PythonCommandResolver() {
    }

    public static List<String> resolve(String configuredPython, String condaEnvName) {
        return resolve(configuredPython, condaEnvName, Path.of(System.getProperty("user.home", ".")));
    }

    static List<String> resolve(String configuredPython, String condaEnvName, Path homeDir) {
        String normalized = configuredPython == null ? "" : configuredPython.trim();
        if (StringUtils.hasText(normalized) && !isExamplePlaceholder(normalized)) {
            return List.of(normalized);
        }

        Path envPython = findCondaEnvPython(homeDir, condaEnvName);
        if (envPython != null) {
            return List.of(envPython.toString());
        }

        return List.of("conda", "run", "-n", condaEnvName, "python");
    }

    private static boolean isExamplePlaceholder(String value) {
        String normalized = value.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.startsWith("/path/to/") || normalized.contains("/path/to/");
    }

    private static Path findCondaEnvPython(Path homeDir, String condaEnvName) {
        for (String root : List.of("miniconda3", "anaconda3", "mambaforge", "miniforge3")) {
            Path candidate = homeDir.resolve(root).resolve("envs").resolve(condaEnvName).resolve("bin/python");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
