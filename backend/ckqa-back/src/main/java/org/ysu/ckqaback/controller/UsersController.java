package org.ysu.ckqaback.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.service.UsersService;
import org.ysu.ckqaback.user.dto.UserCreateRequest;
import org.ysu.ckqaback.user.dto.UserQueryRequest;
import org.ysu.ckqaback.user.dto.UserResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 平台用户表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.USERS)
public class UsersController {

    private final UsersService usersService;

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(UserResponse.fromEntity(usersService.getRequiredById(id)));
    }

    @GetMapping
    public ApiResponse<ApiPageData<UserResponse>> listUsers(@Valid UserQueryRequest request) {
        IPage<Users> page = usersService.pageUsers(
                request.getPage(),
                request.getSize(),
                request.getUsername(),
                request.getStatus()
        );
        List<UserResponse> items = page.getRecords().stream()
                .map(UserResponse::fromEntity)
                .toList();
        return ApiResponseUtils.success(new ApiPageData<>(
                items,
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages()
        ));
    }

    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponseUtils.success(UserResponse.fromEntity(usersService.createUser(request)));
    }
}
