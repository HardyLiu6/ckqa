package org.ysu.ckqaback.integration.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldTerminateTrackedProcessWhenRunnerDestroyed() throws Exception {
        ProcessRunner runner = new ProcessRunner();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<ProcessExecutionResult> future = executorService.submit(() ->
                    runner.run(
                            java.util.List.of("bash", "-lc", "sleep 30"),
                            Path.of("."),
                            Map.of(),
                            Duration.ofSeconds(40),
                            ProcessContext.builder().operation("index").indexRunId(9L).build()
                    )
            );

            while (runner.activeProcessCount() == 0) {
                Thread.sleep(20L);
            }

            runner.destroy();

            try {
                ProcessExecutionResult result = future.get();
                assertThat(result.isTerminatedByShutdown()).isTrue();
                assertThat(result.isTimedOut()).isFalse();
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(IOException.class);
                assertThat(exception.getCause()).hasMessageContaining("读取进程输出失败");
            }

            assertThat(runner.activeProcessCount()).isZero();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldDrainStdoutAndStderrWithoutDeadlock() throws Exception {
        ProcessRunner runner = new ProcessRunner();

        ProcessExecutionResult result = runner.run(
                java.util.List.of(
                        "bash",
                        "-lc",
                        "for i in $(seq 1 5000); do echo out-$i; echo err-$i 1>&2; done"
                ),
                Path.of("."),
                Map.of(),
                Duration.ofSeconds(10),
                ProcessContext.builder().operation("probe").build()
        );

        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getStdout()).contains("out-5000");
        assertThat(result.getStderr()).contains("err-5000");
    }

    @Test
    void shouldWriteStdoutAndStderrToLogFile() throws Exception {
        ProcessRunner runner = new ProcessRunner();
        Path logFile = tempDir.resolve("process.log");

        ProcessExecutionResult result = runner.run(
                java.util.List.of("bash", "-lc", "echo out; echo err >&2"),
                tempDir,
                Map.of(),
                Duration.ofSeconds(5),
                ProcessContext.builder().operation("index").logFile(logFile).build()
        );

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("out");
        assertThat(result.getStderr()).contains("err");
        assertThat(Files.readString(logFile)).contains("[stdout] out").contains("[stderr] err");
    }
}
