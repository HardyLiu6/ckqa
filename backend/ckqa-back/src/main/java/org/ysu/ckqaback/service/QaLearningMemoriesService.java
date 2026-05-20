package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.QaLearningMemories;

import java.util.List;

/**
 * 学习长期记忆服务。
 */
public interface QaLearningMemoriesService extends IService<QaLearningMemories> {

    List<QaLearningMemories> listActiveByScope(Long userId, String courseId, Long knowledgeBaseId, Long indexRunId, int limit);

    void softDeleteForUser(Long id, Long userId);
}
