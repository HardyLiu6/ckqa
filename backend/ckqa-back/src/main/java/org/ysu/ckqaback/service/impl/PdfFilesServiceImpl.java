package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.PdfFiles;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.PdfFilesMapper;
import org.ysu.ckqaback.service.PdfFilesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * PDF文件表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class PdfFilesServiceImpl extends ServiceImpl<PdfFilesMapper, PdfFiles> implements PdfFilesService {

    @Override
    public PdfFiles getRequiredById(Long id) {
        PdfFiles pdfFile = getById(id);
        if (pdfFile == null) {
            throw new BusinessException(ApiResultCode.PDF_FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return pdfFile;
    }

    @Override
    public boolean claimParseStart(Long id) {
        LambdaUpdateWrapper<PdfFiles> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PdfFiles::getId, id)
                .in(PdfFiles::getParseStatus, "pending", "failed")
                .set(PdfFiles::getParseStatus, "processing")
                .setSql("parse_started_at = NOW(), parse_finished_at = NULL, parse_error_msg = NULL");
        return baseMapper.update(null, wrapper) == 1;
    }

    @Override
    public boolean markParseFailedIfStillProcessing(Long id, String errorMessage) {
        LambdaUpdateWrapper<PdfFiles> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PdfFiles::getId, id)
                .eq(PdfFiles::getParseStatus, "processing")
                .set(PdfFiles::getParseStatus, "failed")
                .setSql("parse_finished_at = NOW()")
                .set(PdfFiles::getParseErrorMsg, errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 500)));
        return baseMapper.update(null, wrapper) == 1;
    }

    @Override
    public List<PdfFiles> listByCourseId(String courseId) {
        LambdaQueryWrapper<PdfFiles> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(courseId), PdfFiles::getCourseId, courseId)
                .orderByDesc(PdfFiles::getUploadTime)
                .orderByDesc(PdfFiles::getCreatedAt);
        return list(queryWrapper);
    }
}
