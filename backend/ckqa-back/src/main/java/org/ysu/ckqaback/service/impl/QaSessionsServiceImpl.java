package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.QaSessionsMapper;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.service.QaSessionsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * <p>
 * 问答会话表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class QaSessionsServiceImpl extends ServiceImpl<QaSessionsMapper, QaSessions> implements QaSessionsService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public QaSessions getRequiredById(Long id) {
        QaSessions session = getById(id);
        if (session == null) {
            throw new BusinessException(ApiResultCode.QA_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return session;
    }

    @Override
    public QaSessions createSession(CreateQaSessionRequest request) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        QaSessions session = new QaSessions();
        session.setSessionCode(generateSessionCode());
        session.setUserId(request.getUserId());
        session.setCourseId(StringUtils.hasText(request.getCourseId()) ? request.getCourseId() : null);
        session.setKnowledgeBaseId(request.getKnowledgeBaseId());
        session.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "新建问答会话");
        session.setStatus("active");
        session.setCreatedAt(now);
        save(session);
        return session;
    }

    @Override
    public void touchLastMessageAt(Long id) {
        LambdaUpdateWrapper<QaSessions> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QaSessions::getId, id)
                .set(QaSessions::getLastMessageAt, LocalDateTime.now(SHANGHAI_ZONE));
        baseMapper.update(null, wrapper);
    }

    private String generateSessionCode() {
        String code;
        do {
            code = "qa-" + UUID.randomUUID().toString().replace("-", "");
        } while (exists(new LambdaQueryWrapper<QaSessions>().eq(QaSessions::getSessionCode, code)));
        return code;
    }
}
