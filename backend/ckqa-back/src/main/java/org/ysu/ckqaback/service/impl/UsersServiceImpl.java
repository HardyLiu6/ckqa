package org.ysu.ckqaback.service.impl;

import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.mapper.UsersMapper;
import org.ysu.ckqaback.service.UsersService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 平台用户表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {

}
