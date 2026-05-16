package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeedInfoStoreTest {

    private final SeedInfoStore store = new SeedInfoStore(new ObjectMapper());

    @Test
    void writeAndReadRoundTrip(@TempDir Path candidatesDir) throws Exception {
        SeedInfoStore.SeedInfo info = SeedInfoStore.SeedInfo.builder()
                .seed("graphrag_tuned")
                .autoTunedPromptDir("/tmp/cache/run_3")
                .generatedAt(java.time.OffsetDateTime.parse("2026-05-17T12:00:00+08:00"))
                .buildRunId(18L)
                .build();

        store.write(candidatesDir, info);

        Optional<SeedInfoStore.SeedInfo> read = store.read(candidatesDir);
        assertThat(read).isPresent();
        assertThat(read.get().getSeed()).isEqualTo("graphrag_tuned");
        assertThat(read.get().getBuildRunId()).isEqualTo(18L);
        // 时区偏移必须保留，否则审计文件失真
        assertThat(read.get().getGeneratedAt().getOffset())
                .isEqualTo(java.time.ZoneOffset.ofHours(8));
    }

    @Test
    void readReturnsEmptyWhenFileMissing(@TempDir Path candidatesDir) {
        assertThat(store.read(candidatesDir)).isEmpty();
    }

    @Test
    void readReturnsEmptyOnMalformedFile(@TempDir Path candidatesDir) throws Exception {
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("seed-info.json"), "not json");
        assertThat(store.read(candidatesDir)).isEmpty();
    }

    @Test
    void nullableFieldsRoundTrip(@TempDir Path candidatesDir) throws Exception {
        // 业务真实场景：system_default seed 没有 autoTunedPromptDir；
        // 离线复用历史构建时也可能 buildRunId 缺失。两种 null 都要能正确往返。
        SeedInfoStore.SeedInfo info = SeedInfoStore.SeedInfo.builder()
                .seed("system_default")
                .autoTunedPromptDir(null)
                .generatedAt(java.time.OffsetDateTime.parse("2026-05-17T12:00:00+08:00"))
                .buildRunId(null)
                .build();

        store.write(candidatesDir, info);

        Optional<SeedInfoStore.SeedInfo> read = store.read(candidatesDir);
        assertThat(read).isPresent();
        assertThat(read.get().getSeed()).isEqualTo("system_default");
        assertThat(read.get().getAutoTunedPromptDir()).isNull();
        assertThat(read.get().getBuildRunId()).isNull();
    }

    @Test
    void writeProducesIso8601StringForGeneratedAt(@TempDir Path candidatesDir) throws Exception {
        // 锁定 generatedAt 序列化契约：必须是 ISO-8601 字符串，不能退化为 timestamp 数组
        SeedInfoStore.SeedInfo info = SeedInfoStore.SeedInfo.builder()
                .seed("system_default")
                .autoTunedPromptDir(null)
                .generatedAt(java.time.OffsetDateTime.parse("2026-05-17T12:00:00+08:00"))
                .buildRunId(18L)
                .build();

        store.write(candidatesDir, info);

        String json = Files.readString(candidatesDir.resolve("seed-info.json"));
        // 必须是带 ISO 时间偏移的字符串，不是数字数组也不是缺偏移的本地时间
        assertThat(json).contains("\"generatedAt\" : \"2026-05-17T12:00:00+08:00\"");
        assertThat(json).doesNotContain("\"generatedAt\" : [");  // 否定 timestamp 数组形态
    }
}
