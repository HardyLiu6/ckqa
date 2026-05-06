package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.UsersMapper;
import org.ysu.ckqaback.service.UsersService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.ysu.ckqaback.user.dto.UserCreateRequest;
import org.ysu.ckqaback.user.dto.UserQueryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    @Override
    public Users getRequiredById(Long id) {
        Users user = getById(id);
        if (user == null) {
            throw new BusinessException(ApiResultCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return user;
    }

    @Override
    public IPage<Users> pageUsers(UserQueryRequest request) {
        long current = request.getPage() == null ? 1L : request.getPage();
        long size = request.getSize() == null ? 10L : request.getSize();
        String keyword = trimToNull(request.getKeyword());
        String username = keyword == null ? trimToNull(request.getUsername()) : null;
        return baseMapper.selectUserPage(
                new Page<>(current, size),
                username,
                trimToNull(request.getStatus()),
                trimToNull(request.getRoleCode()),
                keyword
        );
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        if (userId == null || !StringUtils.hasText(roleCode)) {
            return false;
        }
        return baseMapper.countUserRole(userId, roleCode.trim()) > 0;
    }

    @Override
    public Users createUser(UserCreateRequest request) {
        if (count(new LambdaQueryWrapper<Users>().eq(Users::getUserCode, request.getUserCode())) > 0) {
            throw new BusinessException(ApiResultCode.USER_CODE_EXISTS, HttpStatus.CONFLICT);
        }
        if (count(new LambdaQueryWrapper<Users>().eq(Users::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ApiResultCode.USERNAME_EXISTS, HttpStatus.CONFLICT);
        }

        Users user = new Users();
        user.setUserCode(request.getUserCode());
        user.setUsername(request.getUsername());
        user.setDisplayName(request.getDisplayName());
        user.setPasswordHash(trimToNull(request.getPasswordHash()));
        user.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "active");
        save(user);
        return user;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
