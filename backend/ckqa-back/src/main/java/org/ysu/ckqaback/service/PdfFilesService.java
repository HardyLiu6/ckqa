package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.PdfFiles;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * PDF文件表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface PdfFilesService extends IService<PdfFiles> {

    PdfFiles getRequiredById(Long id);

    boolean claimParseStart(Long id);

    boolean markParseFailedIfStillProcessing(Long id, String errorMessage);

    java.util.List<PdfFiles> listByCourseId(String courseId);
}
