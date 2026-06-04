package org.ysu.ckqaback.qa.export;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.qa.dto.QaOperationLogExportRow;

import java.nio.file.Path;
import java.util.List;

/**
 * 问答运维扁平样本 xlsx 导出器。
 */
@Component
public class QaOperationLogXlsxExporter {

    public void write(Path targetFile, List<QaOperationLogExportRow> rows) {
        EasyExcel.write(targetFile.toFile(), QaOperationLogExportRow.class)
                .excelType(ExcelTypeEnum.XLSX)
                .sheet("问答运维样本")
                .doWrite(rows);
    }
}
