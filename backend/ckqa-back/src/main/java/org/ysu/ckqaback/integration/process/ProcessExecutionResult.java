package org.ysu.ckqaback.integration.process;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 外部进程执行结果。
 */
@Getter
@Builder
public class ProcessExecutionResult {

    private final List<String> command;
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final long elapsedSeconds;
    private final boolean timedOut;
    private final boolean terminatedByShutdown;
}
