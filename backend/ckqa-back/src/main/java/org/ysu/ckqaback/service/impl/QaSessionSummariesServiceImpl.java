package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.QaSessionSummaries;
import org.ysu.ckqaback.mapper.QaSessionSummariesMapper;
import org.ysu.ckqaback.service.QaSessionSummariesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 问答会话滚动摘要服务实现。
 */
@Service
public class QaSessionSummariesServiceImpl extends ServiceImpl<QaSessionSummariesMapper, QaSessionSummaries>
        implements QaSessionSummariesService {

    @Override
    public QaSessionSummaries findLatestSuccessfulBySessionId(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        LambdaQueryWrapper<QaSessionSummaries> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QaSessionSummaries::getSessionId, sessionId)
                .eq(QaSessionSummaries::getStatus, "success")
                .orderByDesc(QaSessionSummaries::getSummaryUntilSequenceNo)
                .orderByDesc(QaSessionSummaries::getCreatedAt)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }
}
