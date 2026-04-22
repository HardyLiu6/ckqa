package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.ParseResults;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 解析结果表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface ParseResultsService extends IService<ParseResults> {

    java.util.List<ParseResults> listByPdfFileId(Long pdfFileId);

    java.util.List<ParseResults> listGraphRagOutputs(Long pdfFileId);

    boolean hasCompleteGraphRagExport(Long pdfFileId, String mode, boolean withPageDocs);
}
