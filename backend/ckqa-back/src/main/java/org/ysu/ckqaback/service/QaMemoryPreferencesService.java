package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.QaMemoryPreferences;

/**
 * 长期记忆偏好服务。
 */
public interface QaMemoryPreferencesService extends IService<QaMemoryPreferences> {

    QaMemoryPreferences findByScope(Long userId, String courseId, Long knowledgeBaseId, Long indexRunId);

    QaMemoryPreferences upsertPreference(Long userId, String courseId, Long knowledgeBaseId, Long indexRunId, boolean enabled);
}
