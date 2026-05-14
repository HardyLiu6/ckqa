package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTuneLogTailerTest {

    @TempDir
    Path tempDir;

    @Test
    void parseRelevantMessage_extractsMessageFromValidLine() {
        Optional<String> message = PromptTuneLogTailer.parseRelevantMessage(
                "2026-05-14 13:45:39.0949 - INFO - graphrag.api.prompt_tune - Chunking documents..."
        );
        assertThat(message).contains("Chunking documents...");
    }

    @Test
    void parseRelevantMessage_ignoresNonInfoLines() {
        // 只关心 INFO 行，错误日志在 _archive.log 里被独立处理。
        Optional<String> message = PromptTuneLogTailer.parseRelevantMessage(
                "2026-05-14 13:45:39.0949 - ERROR - graphrag.api.prompt_tune - Some failure"
        );
        assertThat(message).isEmpty();
    }

    @Test
    void parseRelevantMessage_ignoresOtherLoggers() {
        // graphrag_llm.middleware.with_logging 等 logger 会刷大量调试信息，不应被当成进度。
        Optional<String> message = PromptTuneLogTailer.parseRelevantMessage(
                "2026-05-14 13:45:39.0949 - INFO - graphrag_llm.middleware.with_logging - Async request"
        );
        assertThat(message).isEmpty();
    }

    @Test
    void parseRelevantMessage_acceptsCliPromptTuneLogger() {
        Optional<String> message = PromptTuneLogTailer.parseRelevantMessage(
                "2026-05-14 13:49:03.0941 - INFO - graphrag.cli.prompt_tune - Writing prompts to /tmp/x"
        );
        assertThat(message).contains("Writing prompts to /tmp/x");
    }

    @Test
    void parseRelevantMessage_returnsEmptyForGarbage() {
        assertThat(PromptTuneLogTailer.parseRelevantMessage("not a log line")).isEmpty();
        assertThat(PromptTuneLogTailer.parseRelevantMessage("")).isEmpty();
    }

    @Test
    void run_emitsPhasesInOrderAsLinesAreAppended() throws Exception {
        Path logFile = tempDir.resolve("prompt-tuning.log");
        Files.createFile(logFile);

        List<PromptTunePhase> capturedPhases = new CopyOnWriteArrayList<>();
        List<String> capturedLines = new CopyOnWriteArrayList<>();

        PromptTuneLogTailer tailer = new PromptTuneLogTailer(
                logFile,
                0L,
                capturedPhases::add,
                capturedLines::add,
                50L
        );
        Thread thread = new Thread(tailer, "test-tailer");
        thread.setDaemon(true);
        thread.start();
        try {
            appendLines(logFile,
                    "2026-05-14 13:45:39.0949 - INFO - graphrag.api.prompt_tune - Chunking documents...",
                    "2026-05-14 13:46:34.0561 - INFO - graphrag.api.prompt_tune - Generating entity relationship examples...",
                    "2026-05-14 13:48:44.0431 - INFO - graphrag.api.prompt_tune - Generating entity extraction prompt...",
                    "2026-05-14 13:49:03.0942 - INFO - graphrag.cli.prompt_tune - Writing prompts to /tmp/foo"
            );
            // 给 tailer 时间消化（最多等 5 秒）
            for (int i = 0; i < 50 && capturedPhases.size() < 4; i++) {
                Thread.sleep(100);
            }
        } finally {
            tailer.stop();
            thread.join(2000);
        }

        assertThat(capturedPhases).containsExactly(
                PromptTunePhase.CHUNKING,
                PromptTunePhase.EXAMPLES,
                PromptTunePhase.EXTRACT_PROMPT,
                PromptTunePhase.WRITING
        );
        assertThat(capturedLines).hasSize(4);
    }

    @Test
    void run_doesNotRegressPhase() throws Exception {
        // 防御性测试：日志中错乱的旧 INFO 行（例如别的 run 留下的）不能让阶段后退。
        Path logFile = tempDir.resolve("prompt-tuning.log");
        Files.createFile(logFile);

        List<PromptTunePhase> capturedPhases = new CopyOnWriteArrayList<>();

        PromptTuneLogTailer tailer = new PromptTuneLogTailer(
                logFile,
                0L,
                capturedPhases::add,
                line -> {},
                50L
        );
        Thread thread = new Thread(tailer, "test-tailer-no-regression");
        thread.setDaemon(true);
        thread.start();
        try {
            appendLines(logFile,
                    "2026-05-14 13:46:34.0561 - INFO - graphrag.api.prompt_tune - Generating entity relationship examples...",
                    // 后到的 "Chunking documents" 进度低于前一个，应被丢弃。
                    "2026-05-14 13:46:35.0000 - INFO - graphrag.api.prompt_tune - Chunking documents..."
            );
            for (int i = 0; i < 30; i++) {
                Thread.sleep(100);
                if (capturedPhases.size() >= 1) break;
            }
            // 再等 200ms 确认不会进来第二个事件
            Thread.sleep(300);
        } finally {
            tailer.stop();
            thread.join(2000);
        }

        assertThat(capturedPhases).containsExactly(PromptTunePhase.EXAMPLES);
    }

    @Test
    void run_respectsStartOffsetSkippingPriorContent() throws Exception {
        // 验证我们能把"已存在的旧日志"跳过：tailer 创建时记录 startOffset，只读其之后的行。
        Path logFile = tempDir.resolve("prompt-tuning.log");
        appendLines(logFile,
                "2026-05-08 18:50:00.0000 - INFO - graphrag.api.prompt_tune - Generating persona..."
        );
        long offset = PromptTuneLogTailer.currentSize(logFile);
        assertThat(offset).isGreaterThan(0L);

        List<PromptTunePhase> capturedPhases = new CopyOnWriteArrayList<>();
        PromptTuneLogTailer tailer = new PromptTuneLogTailer(
                logFile,
                offset,
                capturedPhases::add,
                line -> {},
                50L
        );
        Thread thread = new Thread(tailer, "test-tailer-offset");
        thread.setDaemon(true);
        thread.start();
        try {
            appendLines(logFile,
                    "2026-05-14 13:45:39.0949 - INFO - graphrag.api.prompt_tune - Chunking documents..."
            );
            for (int i = 0; i < 30 && capturedPhases.isEmpty(); i++) {
                Thread.sleep(100);
            }
        } finally {
            tailer.stop();
            thread.join(2000);
        }

        // 旧 PERSONA 在 offset 之前，不应被消费；只看到 startOffset 之后的 CHUNKING。
        assertThat(capturedPhases).containsExactly(PromptTunePhase.CHUNKING);
    }

    @Test
    void currentSize_returnsZeroForMissingFile() {
        Path missing = tempDir.resolve("nope.log");
        assertThat(PromptTuneLogTailer.currentSize(missing)).isEqualTo(0L);
    }

    private void appendLines(Path file, String... lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        Files.write(
                file,
                sb.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
        );
    }
}
