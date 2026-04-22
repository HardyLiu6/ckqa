package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.mapper.QaMessagesMapper;
import org.ysu.ckqaback.service.QaMessagesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 问答消息表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class QaMessagesServiceImpl extends ServiceImpl<QaMessagesMapper, QaMessages> implements QaMessagesService {

    @Override
    public QaMessages appendUserMessage(Long sessionId, String content) {
        return appendMessage(sessionId, "user", content);
    }

    @Override
    public QaMessages appendAssistantMessage(Long sessionId, String content) {
        return appendMessage(sessionId, "assistant", content);
    }

    @Override
    public List<QaMessages> listBySessionId(Long sessionId) {
        LambdaQueryWrapper<QaMessages> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QaMessages::getSessionId, sessionId)
                .orderByAsc(QaMessages::getSequenceNo);
        return list(queryWrapper);
    }

    private QaMessages appendMessage(Long sessionId, String role, String content) {
        QaMessages message = new QaMessages();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setSequenceNo(nextSequenceNo(sessionId));
        message.setContent(content);
        message.setContentText(content);
        save(message);
        return message;
    }

    private int nextSequenceNo(Long sessionId) {
        LambdaQueryWrapper<QaMessages> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QaMessages::getSessionId, sessionId)
                .orderByDesc(QaMessages::getSequenceNo)
                .last("LIMIT 1");
        QaMessages latest = getOne(queryWrapper, false);
        return latest == null || latest.getSequenceNo() == null ? 1 : latest.getSequenceNo() + 1;
    }
}
