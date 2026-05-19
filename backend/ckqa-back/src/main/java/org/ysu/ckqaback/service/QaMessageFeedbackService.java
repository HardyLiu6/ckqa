package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.entity.QaMessageFeedback;
import org.ysu.ckqaback.qa.dto.QaFeedbackResponse;
import org.ysu.ckqaback.qa.dto.SubmitQaFeedbackRequest;

import java.util.List;
import java.util.Map;

/**
 * 问答消息反馈服务。
 */
public interface QaMessageFeedbackService extends IService<QaMessageFeedback> {

    QaFeedbackResponse upsertFeedback(SubmitQaFeedbackRequest request, AuthenticatedUser currentUser);

    void deleteFeedback(Long messageId, AuthenticatedUser currentUser);

    Map<Long, QaFeedbackResponse> findFeedbackByMessageIdsForUser(List<Long> messageIds, Long userId);
}
