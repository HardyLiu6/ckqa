package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.CourseMaterialsMapper;
import org.ysu.ckqaback.service.CourseMaterialsService;

import java.util.List;

/**
 * <p>
 * 课程资料关系表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-23
 */
@Service
public class CourseMaterialsServiceImpl extends ServiceImpl<CourseMaterialsMapper, CourseMaterials>
        implements CourseMaterialsService {

    private static final int MAX_ERROR_MESSAGE_CODE_POINTS = 500;

    @Override
    public CourseMaterials getRequiredById(Long id) {
        CourseMaterials courseMaterial = getById(id);
        if (courseMaterial == null) {
            throw new BusinessException(ApiResultCode.PDF_FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return courseMaterial;
    }

    @Override
    public boolean claimParseStart(Long id) {
        LambdaUpdateWrapper<CourseMaterials> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CourseMaterials::getId, id)
                .in(CourseMaterials::getParseStatus, "pending", "failed")
                .set(CourseMaterials::getParseStatus, "processing")
                .setSql("parse_started_at = NOW(), parse_finished_at = NULL, parse_error_msg = NULL");
        return baseMapper.update(null, wrapper) == 1;
    }

    @Override
    public boolean markParseFailedIfStillProcessing(Long id, String errorMessage) {
        LambdaUpdateWrapper<CourseMaterials> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CourseMaterials::getId, id)
                .eq(CourseMaterials::getParseStatus, "processing")
                .set(CourseMaterials::getParseStatus, "failed")
                .setSql("parse_finished_at = NOW()")
                .set(CourseMaterials::getParseErrorMsg, truncateByCodePoints(errorMessage));
        return baseMapper.update(null, wrapper) == 1;
    }

    @Override
    public List<CourseMaterials> listByCourseId(String courseId) {
        LambdaQueryWrapper<CourseMaterials> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(courseId), CourseMaterials::getCourseId, courseId)
                .orderByDesc(CourseMaterials::getUploadTime)
                .orderByDesc(CourseMaterials::getCreatedAt);
        return list(queryWrapper);
    }

    private String truncateByCodePoints(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= MAX_ERROR_MESSAGE_CODE_POINTS) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(0, MAX_ERROR_MESSAGE_CODE_POINTS);
        return value.substring(0, endIndex);
    }
}
