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
    public IPage<Users> pageUsers(Long page, Long size, String username, String status) {
        LambdaQueryWrapper<Users> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(Users::getCreatedAt);

        if (StringUtils.hasText(username)) {
            queryWrapper.like(Users::getUsername, username);
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Users::getStatus, status);
        }

        return page(new Page<>(page, size), queryWrapper);
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
        user.setPasswordHash(request.getPasswordHash());
        user.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "active");
        save(user);
        return user;
    }
}
