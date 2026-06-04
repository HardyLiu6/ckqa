package org.ysu.ckqaback.qa.export;

import org.springframework.stereotype.Component;
import org.ysu.ckqaback.qa.dto.QaOperationLogExportRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 问答运维扁平样本 xlsx 导出器。
 *
 * <p>这里直接生成 OpenXML 最小工作簿，避免开发态/运行态被第三方 Excel
 * 依赖链版本差异影响。导出上限当前是 1000 行，JDK 内存生成足够稳定。</p>
 */
@Component
public class QaOperationLogXlsxExporter {

    private static final String SHEET_NAME = "问答运维样本";
    private static final List<Column> COLUMNS = List.of(
            new Column("日志ID", 10, QaOperationLogExportRow::getRetrievalLogId),
            new Column("课程", 24, QaOperationLogExportRow::getCourseName),
            new Column("知识库", 24, QaOperationLogExportRow::getKnowledgeBaseName),
            new Column("学生", 16, QaOperationLogExportRow::getUserDisplay),
            new Column("模式", 12, QaOperationLogExportRow::getQueryMode),
            new Column("查询策略", 20, QaOperationLogExportRow::getQueryStrategy),
            new Column("任务状态", 12, QaOperationLogExportRow::getTaskStatus),
            new Column("路由置信度", 14, QaOperationLogExportRow::getRoutingConfidenceBand),
            new Column("复核优先级", 14, QaOperationLogExportRow::getRoutingReviewPriority),
            new Column("耗时(ms)", 12, QaOperationLogExportRow::getDurationMs),
            new Column("来源数", 10, QaOperationLogExportRow::getSourceCount),
            new Column("有用反馈", 10, QaOperationLogExportRow::getHelpfulCount),
            new Column("无用反馈", 10, QaOperationLogExportRow::getUnhelpfulCount),
            new Column("待改进反馈", 12, QaOperationLogExportRow::getNeedsImprovementCount),
            new Column("来源问题反馈", 14, QaOperationLogExportRow::getSourceIssueCount),
            new Column("创建时间", 20, QaOperationLogExportRow::getCreatedAt)
    );

    public byte[] toByteArray(List<QaOperationLogExportRow> rows) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            writeEntry(zip, "[Content_Types].xml", contentTypesXml());
            writeEntry(zip, "_rels/.rels", packageRelationshipsXml());
            writeEntry(zip, "xl/workbook.xml", workbookXml());
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml());
            writeEntry(zip, "xl/styles.xml", stylesXml());
            writeEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(rows == null ? List.of() : rows));
        }
        return buffer.toByteArray();
    }

    public void write(Path targetFile, List<QaOperationLogExportRow> rows) throws IOException {
        Files.write(targetFile, toByteArray(rows));
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """;
    }

    private static String packageRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private static String workbookXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="%s" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """.formatted(xml(SHEET_NAME));
    }

    private static String workbookRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """;
    }

    private static String stylesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2">
                    <font><sz val="11"/><name val="Arial"/></font>
                    <font><b/><sz val="11"/><name val="Arial"/></font>
                  </fonts>
                  <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                  </cellXfs>
                  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>
                """;
    }

    private static String worksheetXml(List<QaOperationLogExportRow> rows) {
        StringBuilder xml = new StringBuilder(4096 + rows.size() * 512);
        int lastRow = rows.size() + 1;
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<dimension ref=\"A1:").append(columnName(COLUMNS.size())).append(lastRow).append("\"/>");
        xml.append("<cols>");
        for (int i = 0; i < COLUMNS.size(); i++) {
            int index = i + 1;
            xml.append("<col min=\"").append(index)
                    .append("\" max=\"").append(index)
                    .append("\" width=\"").append(COLUMNS.get(i).width())
                    .append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        appendRow(xml, 1, headerCells(), true);
        for (int i = 0; i < rows.size(); i++) {
            appendRow(xml, i + 2, rowCells(rows.get(i)), false);
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static List<Object> headerCells() {
        return COLUMNS.stream().map(Column::header).map(Object.class::cast).toList();
    }

    private static List<Object> rowCells(QaOperationLogExportRow row) {
        List<Object> values = new ArrayList<>(COLUMNS.size());
        for (Column column : COLUMNS) {
            values.add(column.value(row));
        }
        return values;
    }

    private static void appendRow(StringBuilder xml, int rowNumber, List<Object> values, boolean header) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            String ref = columnName(i + 1) + rowNumber;
            if (value instanceof Number number) {
                xml.append("<c r=\"").append(ref).append("\"><v>").append(number).append("</v></c>");
            } else {
                xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
                if (header) {
                    xml.append(" s=\"1\"");
                }
                xml.append("><is><t>").append(xml(value)).append("</t></is></c>");
            }
        }
        xml.append("</row>");
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        while (value > 0) {
            value--;
            name.insert(0, (char) ('A' + (value % 26)));
            value /= 26;
        }
        return name.toString();
    }

    private static String xml(Object value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        String text = String.valueOf(value);
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (!isXmlChar(codePoint)) {
                continue;
            }
            switch (codePoint) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.appendCodePoint(codePoint);
            }
        }
        return out.toString();
    }

    private static boolean isXmlChar(int codePoint) {
        return codePoint == 0x09
                || codePoint == 0x0A
                || codePoint == 0x0D
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    private record Column(String header, int width, ValueExtractor extractor) {
        Object value(QaOperationLogExportRow row) {
            return extractor.value(row);
        }
    }

    @FunctionalInterface
    private interface ValueExtractor {
        Object value(QaOperationLogExportRow row);
    }
}
