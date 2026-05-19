package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.QaSessionSummaries;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 问答会话滚动摘要服务。
 */
public interface QaSessionSummariesService extends IService<QaSessionSummaries> {

    QaSessionSummaries findLatestSuccessfulBySessionId(Long sessionId);
}
