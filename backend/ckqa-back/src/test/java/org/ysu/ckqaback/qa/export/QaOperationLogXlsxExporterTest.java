package org.ysu.ckqaback.qa.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.qa.dto.QaOperationLogExportRow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaOperationLogXlsxExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteXlsxFile() throws Exception {
        Path targetFile = tempDir.resolve("qa-operation-samples.xlsx");

        new QaOperationLogXlsxExporter().write(targetFile, List.of(buildExportRow()));

        byte[] body = Files.readAllBytes(targetFile);
        assertThat(body).hasSizeGreaterThan(2);
        assertThat(body[0] & 0xFF).isEqualTo(0x50);
        assertThat(body[1] & 0xFF).isEqualTo(0x4B);
    }

    private QaOperationLogExportRow buildExportRow() {
        QaOperationLogExportRow row = new QaOperationLogExportRow();
        row.setRetrievalLogId(42L);
        row.setCourseName("操作系统2026春");
        row.setKnowledgeBaseName("操作系统教材主知识库");
        row.setUserDisplay("周子涵");
        row.setQueryMode("global");
        row.setQueryStrategy("cli / 已降级");
        row.setTaskStatus("success");
        row.setRoutingConfidenceBand("high_confidence");
        row.setRoutingReviewPriority("normal");
        row.setDurationMs(196_000L);
        row.setSourceCount(23L);
        row.setHelpfulCount(2L);
        row.setUnhelpfulCount(0L);
        row.setNeedsImprovementCount(0L);
        row.setSourceIssueCount(0L);
        row.setCreatedAt("2026-05-29 09:42:00");
        return row;
    }
}
