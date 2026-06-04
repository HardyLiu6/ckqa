package org.ysu.ckqaback.qa.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.qa.dto.QaOperationLogExportRow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

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
        try (ZipFile zip = new ZipFile(targetFile.toFile())) {
            assertThat(zip.getEntry("xl/workbook.xml")).isNotNull();
            assertThat(zip.getEntry("xl/worksheets/sheet1.xml")).isNotNull();
            String sheet = new String(zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
            assertThat(sheet).contains("日志ID");
            assertThat(sheet).contains("操作系统2026春");
        }
    }

    @Test
    void shouldWriteExportLimitRows() throws Exception {
        Path targetFile = tempDir.resolve("qa-operation-samples-large.xlsx");
        List<QaOperationLogExportRow> rows = new ArrayList<>();
        for (long i = 1; i <= 1000; i++) {
            QaOperationLogExportRow row = buildExportRow();
            row.setRetrievalLogId(i);
            rows.add(row);
        }

        new QaOperationLogXlsxExporter().write(targetFile, rows);

        byte[] body = Files.readAllBytes(targetFile);
        assertThat(body).hasSizeGreaterThan(2);
        assertThat(body[0] & 0xFF).isEqualTo(0x50);
        assertThat(body[1] & 0xFF).isEqualTo(0x4B);
    }

    @Test
    void shouldUseJdkOnlyRuntime() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/ysu/ckqaback/qa/export/QaOperationLogXlsxExporter.java"));

        assertThat(source).doesNotContain("com.alibaba.excel");
        assertThat(source).doesNotContain("org.apache.poi");
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
