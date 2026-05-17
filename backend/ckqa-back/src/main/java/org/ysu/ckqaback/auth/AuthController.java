package org.ysu.ckqaback.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.dto.AuthLoginRequest;
import org.ysu.ckqaback.auth.dto.AuthRegisterRequest;
import org.ysu.ckqaback.auth.dto.AuthResponse;
import org.ysu.ckqaback.auth.dto.AuthUserProfile;
import org.ysu.ckqaback.auth.dto.ChangePasswordRequest;
import org.ysu.ckqaback.auth.dto.UpdateProfileRequest;

/**
 * JWT 认证接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.AUTH)
public class AuthController {

    private final AuthService authService;

    @PostMapping("/admin/login")
    public ApiResponse<AuthResponse> loginAdmin(@Valid @RequestBody AuthLoginRequest request) {
        return ApiResponseUtils.success(authService.loginForAudience(request, "admin"));
    }

    @PostMapping("/student/login")
    public ApiResponse<AuthResponse> loginStudent(@Valid @RequestBody AuthLoginRequest request) {
        return ApiResponseUtils.success(authService.loginForAudience(request, "student"));
    }

    @PostMapping("/student/register")
    public ApiResponse<AuthResponse> registerStudent(@Valid @RequestBody AuthRegisterRequest request) {
        return ApiResponseUtils.success(authService.registerStudent(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserProfile> getCurrentUser(HttpServletRequest request) {
        return ApiResponseUtils.success(authService.getCurrentProfile(AuthContext.fromRequestOrCurrentJwt(request)));
    }

    /**
     * 个人中心：更新当前用户的显示名 / 邮箱 / 手机号。
     */
    @PutMapping("/me")
    public ApiResponse<AuthUserProfile> updateCurrentUser(
            HttpServletRequest request,
            @Valid @RequestBody UpdateProfileRequest body
    ) {
        AuthenticatedUser current = AuthContext.fromRequestOrCurrentJwt(request);
        return ApiResponseUtils.success(
                authService.updateCurrentProfile(current, body.getDisplayName(), body.getEmail(), body.getPhone())
        );
    }

    /**
     * 个人中心：修改当前用户密码。
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changeCurrentPassword(
            HttpServletRequest request,
            @Valid @RequestBody ChangePasswordRequest body
    ) {
        AuthenticatedUser current = AuthContext.fromRequestOrCurrentJwt(request);
        authService.changeCurrentPassword(current, body.getOldPassword(), body.getNewPassword());
        return ApiResponseUtils.success(null);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AuthUserProfile> uploadCurrentUserAvatar(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponseUtils.success(authService.uploadCurrentUserAvatar(AuthContext.fromRequestOrCurrentJwt(request), file));
    }
}
