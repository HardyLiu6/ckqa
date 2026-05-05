package org.ysu.ckqaback.integration.process;

import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;

/**
 * 外部进程运行时的业务上下文。
 */
@Getter
@Builder
public class ProcessContext {

    private final String operation;
    private final Long pdfFileId;
    private final Long indexRunId;
    private final Path logFile;
}
