package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.KnowledgeBasesMapper;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <p>
 * 课程知识库表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class KnowledgeBasesServiceImpl extends ServiceImpl<KnowledgeBasesMapper, KnowledgeBases> implements KnowledgeBasesService {

    @Override
    public KnowledgeBases getRequiredById(Long id) {
        KnowledgeBases knowledgeBase = getById(id);
        if (knowledgeBase == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return knowledgeBase;
    }

    @Override
    public List<KnowledgeBases> listByCourseId(String courseId) {
        LambdaQueryWrapper<KnowledgeBases> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.hasText(courseId), KnowledgeBases::getCourseId, courseId)
                .orderByDesc(KnowledgeBases::getCreatedAt);
        return list(queryWrapper);
    }

    @Override
    public void updateActiveIndexRunId(Long knowledgeBaseId, Long indexRunId) {
        LambdaUpdateWrapper<KnowledgeBases> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(KnowledgeBases::getId, knowledgeBaseId)
                .set(KnowledgeBases::getActiveIndexRunId, indexRunId);
        baseMapper.update(null, wrapper);
    }
}
