package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.mapper.QaMessagesMapper;
import org.ysu.ckqaback.service.QaMessagesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

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

    @Override
    public int copyMessagesToSession(Long sourceSessionId, Long targetSessionId, Integer boundarySequenceNo) {
        if (boundarySequenceNo == null) {
            return 0;
        }
        List<QaMessages> sourceMessages = listBySessionId(sourceSessionId).stream()
                .filter(message -> message.getSequenceNo() != null && message.getSequenceNo() <= boundarySequenceNo)
                .toList();
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        for (QaMessages source : sourceMessages) {
            QaMessages copy = new QaMessages();
            copy.setSessionId(targetSessionId);
            copy.setRole(source.getRole());
            copy.setSequenceNo(source.getSequenceNo());
            copy.setContent(source.getContent());
            copy.setContentText(source.getContentText());
            copy.setTokenCount(source.getTokenCount());
            copy.setCopiedFromMessageId(source.getId());
            copy.setCreatedAt(now);
            save(copy);
        }
        return sourceMessages.size();
    }

    private QaMessages appendMessage(Long sessionId, String role, String content) {
        QaMessages message = new QaMessages();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setSequenceNo(nextSequenceNo(sessionId));
        message.setContent(content);
        message.setContentText(content);
        message.setCreatedAt(LocalDateTime.now(SHANGHAI_ZONE));
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
