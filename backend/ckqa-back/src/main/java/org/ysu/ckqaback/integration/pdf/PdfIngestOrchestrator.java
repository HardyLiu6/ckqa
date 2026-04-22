package org.ysu.ckqaback.integration.pdf;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.PdfFiles;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.process.ProcessContext;
import org.ysu.ckqaback.integration.process.ProcessExecutionResult;
import org.ysu.ckqaback.integration.process.ProcessRunner;
import org.ysu.ckqaback.pdf.dto.ExportGraphRagRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 调用 pdf_ingest CLI 的编排适配层。
 */
@Service
@RequiredArgsConstructor
public class PdfIngestOrchestrator {

    private final CkqaIntegrationProperties properties;
    private final ProcessRunner processRunner;

    public ProcessExecutionResult parse(PdfFiles pdfFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                properties.getPdfIngest().getPython(),
                "scripts/pdf_processor/mineru_parser.py",
                "parse",
                pdfFile.getCourseId(),
                "--file-id",
                String.valueOf(pdfFile.getId())
        );
        return processRunner.run(
                command,
                Path.of(properties.getPdfIngest().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getParseSeconds()),
                ProcessContext.builder()
                        .operation("parse")
                        .pdfFileId(pdfFile.getId())
                        .build()
        );
    }

    public ProcessExecutionResult exportGraphRag(PdfFiles pdfFile, ExportGraphRagRequest request)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                properties.getPdfIngest().getPython(),
                "scripts/pdf_processor/mineru_parser.py",
                "export-graphrag",
                pdfFile.getCourseId(),
                "--file-id",
                String.valueOf(pdfFile.getId()),
                "--mode",
                request.getMode()
        ));
        if (request.isWithPageDocs()) {
            command.add("--with-page-docs");
        }
        if (request.isForce()) {
            command.add("--force");
        }
        return processRunner.run(
                command,
                Path.of(properties.getPdfIngest().getRoot()),
                Map.of(),
                Duration.ofSeconds(properties.getTimeout().getExportSeconds()),
                ProcessContext.builder()
                        .operation("export-graphrag")
                        .pdfFileId(pdfFile.getId())
                        .build()
        );
    }
}
