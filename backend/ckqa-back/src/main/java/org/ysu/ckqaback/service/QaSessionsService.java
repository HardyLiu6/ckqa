package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.QaSessions;
import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;

/**
 * <p>
 * 问答会话表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface QaSessionsService extends IService<QaSessions> {

    QaSessions getRequiredById(Long id);

    QaSessions createSession(CreateQaSessionRequest request);

    void touchLastMessageAt(Long id);
}
