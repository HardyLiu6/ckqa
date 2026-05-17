package org.ysu.ckqaback.index;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 构建流水线工作区路径服务。
 */
@Service
public class BuildRunWorkspaceService {

    private final Path buildRunsRoot;

    @Autowired
    public BuildRunWorkspaceService(CkqaIntegrationProperties properties) {
        this(defaultRoot(properties));
    }

    public BuildRunWorkspaceService(String buildRunsRoot) {
        if (!StringUtils.hasText(buildRunsRoot)) {
            throw new IllegalArgumentException("GraphRAG 构建工作区根目录不能为空");
        }
        this.buildRunsRoot = Path.of(buildRunsRoot).toAbsolutePath().normalize();
    }

    public String workspaceUri(Long userId, Long knowledgeBaseId, Long buildRunId) {
        long safeUserId = userId == null ? 0L : userId;
        return "user_" + safeUserId + "/kb_" + knowledgeBaseId + "/build_" + buildRunId;
    }

    public Path resolve(String workspaceUri) {
        if (!StringUtils.hasText(workspaceUri)) {
            throw illegalWorkspaceUri();
        }
        Path resolved = buildRunsRoot.resolve(workspaceUri).normalize();
        if (!resolved.startsWith(buildRunsRoot)) {
            throw illegalWorkspaceUri();
        }
        return resolved;
    }

    /**
     * 把绝对路径相对化到 build runs root，便于持久化 build run / eval / candidate 等子目录路径
     * 而不灌机器绝对路径到 DB。绝对路径不在 root 下时返回 null，调用方按"配置异常"兜底。
     *
     * <p>形式：传入 {@code <root>/user_X/kb_Y/build_Z/eval/<evalRunId>}，返回
     * {@code "user_X/kb_Y/build_Z/eval/<evalRunId>"}（始终用 '/' 分隔，跨平台一致）。</p>
     */
    public String relativize(Path absolute) {
        if (absolute == null) return null;
        Path normalized = absolute.toAbsolutePath().normalize();
        if (!normalized.startsWith(buildRunsRoot)) {
            return null;
        }
        return buildRunsRoot.relativize(normalized).toString().replace('\\', '/');
    }

    public void createLayout(String workspaceUri) throws IOException {
        Path root = resolve(workspaceUri);
        for (String directory : new String[]{
                "selection",
                "parse",
                "graph-input",
                "prompt",
                "prompt/candidates",
                "eval",
                "index/input",
                "index/output",
                "index/cache",
                "index/reports",
                "index/logs",
                "qa-smoke"
        }) {
            Files.createDirectories(root.resolve(directory));
        }
    }

    private static String defaultRoot(CkqaIntegrationProperties properties) {
        String configured = properties.getGraphrag().getBuildRunsRoot();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String graphragRoot = properties.getGraphrag().getRoot();
        if (StringUtils.hasText(graphragRoot)) {
            return Path.of(graphragRoot, "runtime", "kb-build-runs").toString();
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "ckqa-graphrag-build-runs", "kb-build-runs").toString();
    }

    private BusinessException illegalWorkspaceUri() {
        return new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "构建工作区路径非法");
    }
}
