package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.index.dto.CandidateResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 读取 build run workspace 下的 {@code prompt/candidates/manifest.json}，
 * 反序列化为 {@link CandidateResponse} 列表。
 *
 * <p>负责：</p>
 * <ul>
 *   <li>透传 manifest 的算法产物字段（schema_used / fewshot_example_count 等）</li>
 *   <li>从 {@link CandidateMetadataLookup} 注入展示层字段（displayNameZh 等）</li>
 *   <li>计算 estimatedTokenPerCall（promptSizeBytes / 4 + 200）</li>
 *   <li>简化 base_prompt_source（绝对路径只取文件名，避免暴露服务器路径）</li>
 *   <li>candidate 不在白名单时跳过，不抛异常</li>
 *   <li>manifest 文件缺失时返回空列表（让上层判断是否需要触发生成）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CandidateManifestReader {

    private static final Logger log = LoggerFactory.getLogger(CandidateManifestReader.class);

    /** 单次抽取输入文本预估 token（中文按 1 token ≈ 1 字符），加在 prompt 长度上估总 token。 */
    private static final int INPUT_TOKEN_OVERHEAD = 200;

    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;

    /**
     * 读 candidatesDir 下的 manifest.json 转 CandidateResponse 列表。
     *
     * @param candidatesDir build_run workspace 下的 prompt/candidates 目录
     * @return 候选列表；目录或 manifest 不存在时返回空列表
     * @throws IOException manifest 文件存在但格式损坏
     */
    public List<CandidateResponse> read(Path candidatesDir) throws IOException {
        Path manifestFile = candidatesDir.resolve("manifest.json");
        if (!Files.exists(manifestFile)) {
            return List.of();
        }

        Map<String, Object> root;
        try {
            String json = Files.readString(manifestFile);
            root = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IOException("解析 candidate manifest 失败: " + manifestFile, e);
        }

        Object candidatesNode = root.get("candidates");
        if (!(candidatesNode instanceof List<?> rawList)) {
            return List.of();
        }

        List<CandidateResponse> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) rawMap;
            String candidateId = stringField(entry, "candidate_name");
            if (candidateId == null || !metadataLookup.isKnown(candidateId)) {
                if (candidateId != null) {
                    log.warn("Candidate {} 不在白名单，跳过", candidateId);
                }
                continue;
            }
            result.add(buildResponse(candidateId, entry, candidatesDir));
        }
        return result;
    }

    private CandidateResponse buildResponse(String candidateId, Map<String, Object> entry, Path candidatesDir) {
        int promptSizeBytes = resolvePromptSizeBytes(entry, candidateId, candidatesDir);
        int estimatedTokenPerCall = promptSizeBytes / 4 + INPUT_TOKEN_OVERHEAD;
        return CandidateResponse.builder()
                .candidateId(candidateId)
                .displayNameZh(metadataLookup.displayNameZh(candidateId))
                .category(metadataLookup.category(candidateId))
                .description(metadataLookup.description(candidateId))
                .isRecommended(metadataLookup.isRecommended(candidateId))
                .traits(metadataLookup.traits(candidateId))
                .estimatedTokenPerCall(estimatedTokenPerCall)
                .promptSizeBytes(promptSizeBytes)
                .schemaUsed(boolField(entry, "schema_used"))
                .fewshotExampleCount(intField(entry, "fewshot_example_count"))
                .fewshotStrategy(stringField(entry, "fewshot_strategy"))
                .basePromptSource(simplifyBasePromptSource(stringField(entry, "base_prompt_source")))
                .generationTime(parseTime(stringField(entry, "generation_time")))
                .build();
    }

    private int resolvePromptSizeBytes(Map<String, Object> entry, String candidateId, Path candidatesDir) {
        Integer fromManifest = intField(entry, "prompt_size_bytes");
        if (fromManifest != null && fromManifest > 0) return fromManifest;
        // Fallback：读 prompt.txt 文件大小
        Path promptFile = candidatesDir.resolve(candidateId).resolve("prompt.txt");
        if (Files.exists(promptFile)) {
            try {
                return Math.toIntExact(Files.size(promptFile));
            } catch (IOException e) {
                log.warn("读取 {} 文件大小失败：{}", promptFile, e.getMessage());
            }
        }
        return 0;
    }

    private static String simplifyBasePromptSource(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        // 绝对路径（含/home/…）：只显示文件名，避免暴露服务器路径
        if (raw.startsWith("/")) {
            int slash = raw.lastIndexOf('/');
            return slash >= 0 && slash < raw.length() - 1 ? raw.substring(slash + 1) : raw;
        }
        return raw;
    }

    private static String stringField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value instanceof String s ? s : null;
    }

    private static Integer intField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        if (value instanceof Number num) return num.intValue();
        return null;
    }

    private static Boolean boolField(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value instanceof Boolean b ? b : null;
    }

    private static LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // manifest 用 ISO-8601 含时区（"+08:00"）
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception e) {
            // 兼容无时区格式
            try {
                return LocalDateTime.parse(raw);
            } catch (Exception ignore) {
                return null;
            }
        }
    }
}
