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
import org.ysu.ckqaback.auth.dto.EmailCodeSendRequest;
import org.ysu.ckqaback.auth.dto.EmailLoginRequest;
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
    public ApiResponse<AuthResponse> loginAdmin(
            HttpServletRequest request,
            @Valid @RequestBody AuthLoginRequest body
    ) {
        return ApiResponseUtils.success(
                authService.loginForAudience(body, "admin", AuthService.resolveClientIp(request))
        );
    }

    @PostMapping("/student/login")
    public ApiResponse<AuthResponse> loginStudent(
            HttpServletRequest request,
            @Valid @RequestBody AuthLoginRequest body
    ) {
        return ApiResponseUtils.success(
                authService.loginForAudience(body, "student", AuthService.resolveClientIp(request))
        );
    }

    /** 申请邮箱登录验证码（限频 + 人机验证）。 */
    @PostMapping("/email/send-code")
    public ApiResponse<Void> sendEmailLoginCode(
            HttpServletRequest request,
            @Valid @RequestBody EmailCodeSendRequest body
    ) {
        authService.sendEmailLoginCode(body.getEmail(), body.getTurnstileToken(), AuthService.resolveClientIp(request));
        return ApiResponseUtils.success(null);
    }

    /** 邮箱验证码登录（管理员/教师 audience）。 */
    @PostMapping("/email/admin/login")
    public ApiResponse<AuthResponse> loginAdminByEmail(
            HttpServletRequest request,
            @Valid @RequestBody EmailLoginRequest body
    ) {
        return ApiResponseUtils.success(
                authService.loginByEmailCode(
                        body.getEmail(),
                        body.getCode(),
                        body.getTurnstileToken(),
                        AuthService.resolveClientIp(request),
                        "admin"
                )
        );
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
