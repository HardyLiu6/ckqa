package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.entity.QaSourceReviews;
import org.ysu.ckqaback.qa.dto.QaSourceReviewResponse;
import org.ysu.ckqaback.qa.dto.UpsertQaSourceReviewRequest;

import java.util.List;
import java.util.Map;

/**
 * 问答来源人工标注服务。
 */
public interface QaSourceReviewsService extends IService<QaSourceReviews> {

    QaSourceReviewResponse upsertReview(Long retrievalHitId, UpsertQaSourceReviewRequest request, AuthenticatedUser currentUser);

    Map<Long, List<QaSourceReviewResponse>> findReviewsByHitIds(List<Long> retrievalHitIds);
}
