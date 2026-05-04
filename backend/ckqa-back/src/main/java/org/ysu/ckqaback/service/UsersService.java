package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.ysu.ckqaback.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.user.dto.UserCreateRequest;
import org.ysu.ckqaback.user.dto.UserQueryRequest;

/**
 * <p>
 * 平台用户表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface UsersService extends IService<Users> {

    Users getRequiredById(Long id);

    IPage<Users> pageUsers(UserQueryRequest request);

    boolean hasRole(Long userId, String roleCode);

    Users createUser(UserCreateRequest request);
}
