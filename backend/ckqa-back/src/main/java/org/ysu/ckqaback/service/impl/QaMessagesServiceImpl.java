package org.ysu.ckqaback.service.impl;

import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.mapper.QaMessagesMapper;
import org.ysu.ckqaback.service.QaMessagesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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

}
