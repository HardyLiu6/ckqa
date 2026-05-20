package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.QaLearningMemories;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.QaLearningMemoriesMapper;
import org.ysu.ckqaback.service.QaLearningMemoriesService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 学习长期记忆服务实现。
 */
@Service
public class QaLearningMemoriesServiceImpl
        extends ServiceImpl<QaLearningMemoriesMapper, QaLearningMemories>
        implements QaLearningMemoriesService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public List<QaLearningMemories> listActiveByScope(Long userId, String courseId, Long knowledgeBaseId, Long indexRunId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return list(new LambdaQueryWrapper<QaLearningMemories>()
                .eq(QaLearningMemories::getUserId, userId)
                .eq(QaLearningMemories::getCourseId, courseId)
                .eq(QaLearningMemories::getKnowledgeBaseId, knowledgeBaseId)
                .eq(QaLearningMemories::getIndexRunId, indexRunId)
                .eq(QaLearningMemories::getStatus, "active")
                .orderByDesc(QaLearningMemories::getUpdatedAt)
                .orderByDesc(QaLearningMemories::getCreatedAt)
                .orderByDesc(QaLearningMemories::getId)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public void softDeleteForUser(Long id, Long userId) {
        QaLearningMemories memory = getOne(new LambdaQueryWrapper<QaLearningMemories>()
                .eq(QaLearningMemories::getId, id)
                .eq(QaLearningMemories::getUserId, userId)
                .last("LIMIT 1"), false);
        if (memory == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "学习记忆不存在");
        }
        baseMapper.update(null, new LambdaUpdateWrapper<QaLearningMemories>()
                .eq(QaLearningMemories::getId, id)
                .eq(QaLearningMemories::getUserId, userId)
                .set(QaLearningMemories::getStatus, "deleted")
                .set(QaLearningMemories::getUpdatedAt, LocalDateTime.now(SHANGHAI_ZONE)));
    }
}
