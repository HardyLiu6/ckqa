package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 旁路存储：把候选生成时使用的 seed / 底板路径 / 时间写到
 * {@code <workspace>/prompt/candidates/seed-info.json}。
 *
 * <p>不修改脚本输出（manifest.json 由 Python 写），只在 Java 侧补充审计元数据。
 * Reader 侧把这份信息注入 CandidateResponse.seed。</p>
 */
@Component
@RequiredArgsConstructor
public class SeedInfoStore {

    private static final Logger log = LoggerFactory.getLogger(SeedInfoStore.class);

    private static final String FILE_NAME = "seed-info.json";

    private final ObjectMapper objectMapper;

    public void write(Path candidatesDir, SeedInfo info) throws IOException {
        Files.createDirectories(candidatesDir);
        Path file = candidatesDir.resolve(FILE_NAME);
        // 显式锁定时间序列化契约：JavaTimeModule 启用 + 关闭 timestamp 模式，
        // 确保 OffsetDateTime 输出为 ISO-8601 字符串（含时区偏移），与文档示例和 A8 自检一致。
        ObjectMapper writer = objectMapper.copy()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Files.writeString(file, writer.writeValueAsString(info), StandardCharsets.UTF_8);
    }

    public Optional<SeedInfo> read(Path candidatesDir) {
        Path file = candidatesDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return Optional.empty();
        try {
            ObjectMapper reader = objectMapper.copy()
                    .findAndRegisterModules()
                    .disable(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.ofNullable(reader.readValue(json, SeedInfo.class));
        } catch (Exception e) {
            log.warn("解析 seed-info.json 失败 path={}", file, e);
            return Optional.empty();
        }
    }

    @Getter
    @Builder
    @Jacksonized
    public static class SeedInfo {
        private final String seed;
        private final String autoTunedPromptDir;
        /**
         * 候选生成时间，含时区偏移以便审计文件跨服务器仍能正确呈现。
         * Jackson 通过 JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false 输出 ISO-8601 字符串。
         */
        private final OffsetDateTime generatedAt;
        private final Long buildRunId;
    }
}
