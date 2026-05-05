package org.ysu.ckqaback.integration.process;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一执行外部命令并跟踪活跃子进程。
 * <p>
 * 正常停机过程中，子进程输出读取线程可能因为执行器关闭而被中断，
 * 由此产生的“读取进程输出失败”属于可接受的停机路径表现，
 * 不应直接按业务失败语义解读。
 * </p>
 */
@Component
public class ProcessRunner implements DisposableBean {

    private final ConcurrentMap<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final ExecutorService streamReaderExecutor = Executors.newCachedThreadPool();

    public ProcessExecutionResult run(
            List<String> command,
            Path workDir,
            Map<String, String> environment,
            Duration timeout,
            ProcessContext context
    ) throws IOException, InterruptedException {
        long key = sequence.getAndIncrement();
        long startedAt = System.nanoTime();

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.environment().putAll(environment);

        Process process = builder.start();
        activeProcesses.put(key, process);

        Future<String> stdoutFuture = streamReaderExecutor.submit(() ->
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        Future<String> stderrFuture = streamReaderExecutor.submit(() ->
                new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();
            writeLogFile(context, stdout, stderr);

            return ProcessExecutionResult.builder()
                    .command(command)
                    .exitCode(finished ? process.exitValue() : -1)
                    .stdout(stdout)
                    .stderr(stderr)
                    .elapsedSeconds(Duration.ofNanos(System.nanoTime() - startedAt).toSeconds())
                    .timedOut(!finished)
                    .terminatedByShutdown(shutdownRequested.get())
                    .build();
        } catch (ExecutionException ex) {
            throw new IOException("读取进程输出失败", ex);
        } finally {
            activeProcesses.remove(key);
        }
    }

    private void writeLogFile(ProcessContext context, String stdout, String stderr) throws IOException {
        if (context == null || context.getLogFile() == null) {
            return;
        }
        Path logFile = context.getLogFile();
        Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder builder = new StringBuilder();
        appendLogLines(builder, "stdout", stdout);
        appendLogLines(builder, "stderr", stderr);
        Files.writeString(
                logFile,
                builder.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private void appendLogLines(StringBuilder builder, String streamName, String output) {
        if (output == null || output.isEmpty()) {
            return;
        }
        for (String line : output.split("\\R", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            builder.append('[')
                    .append(streamName)
                    .append("] ")
                    .append(line)
                    .append(System.lineSeparator());
        }
    }

    int activeProcessCount() {
        return activeProcesses.size();
    }

    @Override
    public void destroy() {
        shutdownRequested.set(true);
        activeProcesses.values().forEach(process -> {
            process.destroy();
            try {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        });
        activeProcesses.clear();
        streamReaderExecutor.shutdownNow();
    }
}
