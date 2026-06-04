package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.QaMessages;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 问答消息表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface QaMessagesService extends IService<QaMessages> {

    QaMessages appendUserMessage(Long sessionId, String content);

    QaMessages appendAssistantMessage(Long sessionId, String content);

    List<QaMessages> listBySessionId(Long sessionId);

    int copyMessagesToSession(Long sourceSessionId, Long targetSessionId, Integer boundarySequenceNo);
}
