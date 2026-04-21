package org.ysu.ckqaback.service.impl;

import org.ysu.ckqaback.entity.AuthIdentities;
import org.ysu.ckqaback.mapper.AuthIdentitiesMapper;
import org.ysu.ckqaback.service.AuthIdentitiesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 认证身份扩展表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class AuthIdentitiesServiceImpl extends ServiceImpl<AuthIdentitiesMapper, AuthIdentities> implements AuthIdentitiesService {

}
