package org.ysu.ckqaback.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.dto.AuthLoginRequest;
import org.ysu.ckqaback.auth.dto.AuthRegisterRequest;
import org.ysu.ckqaback.auth.dto.AuthResponse;
import org.ysu.ckqaback.auth.dto.AuthUserProfile;

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
}
