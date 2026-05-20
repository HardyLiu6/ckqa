package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.QaMemoryPreferences;
import org.ysu.ckqaback.mapper.QaMemoryPreferencesMapper;
import org.ysu.ckqaback.service.QaMemoryPreferencesService;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 长期记忆偏好服务实现。
 */
@Service
public class QaMemoryPreferencesServiceImpl
        extends ServiceImpl<QaMemoryPreferencesMapper, QaMemoryPreferences>
        implements QaMemoryPreferencesService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public QaMemoryPreferences findByScope(Long userId, String courseId, Long knowledgeBaseId, Long indexRunId) {
        return getOne(new LambdaQueryWrapper<QaMemoryPreferences>()
                .eq(QaMemoryPreferences::getUserId, userId)
                .eq(QaMemoryPreferences::getCourseId, courseId)
                .eq(QaMemoryPreferences::getKnowledgeBaseId, knowledgeBaseId)
                .eq(QaMemoryPreferences::getIndexRunId, indexRunId)
                .last("LIMIT 1"), false);
    }

    @Override
    public QaMemoryPreferences upsertPreference(Long userId, String courseId, Long knowledgeBaseId, Long indexRunId, boolean enabled) {
        QaMemoryPreferences existing = findByScope(userId, courseId, knowledgeBaseId, indexRunId);
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        if (existing != null) {
            baseMapper.update(null, new LambdaUpdateWrapper<QaMemoryPreferences>()
                    .eq(QaMemoryPreferences::getId, existing.getId())
                    .set(QaMemoryPreferences::getEnabled, enabled)
                    .set(QaMemoryPreferences::getUpdatedAt, now));
            existing.setEnabled(enabled);
            existing.setUpdatedAt(now);
            return existing;
        }

        QaMemoryPreferences preference = new QaMemoryPreferences();
        preference.setUserId(userId);
        preference.setCourseId(courseId);
        preference.setKnowledgeBaseId(knowledgeBaseId);
        preference.setIndexRunId(indexRunId);
        preference.setEnabled(enabled);
        preference.setCreatedAt(now);
        preference.setUpdatedAt(now);
        save(preference);
        return preference;
    }
}
