package org.ysu.ckqaback.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.auth.dto.AuthLoginRequest;
import org.ysu.ckqaback.auth.dto.AuthRegisterRequest;
import org.ysu.ckqaback.auth.dto.AuthResponse;
import org.ysu.ckqaback.auth.dto.AuthUserProfile;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerWebMvcTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldLoginAdminConsoleUser() throws Exception {
        given(authService.loginForAudience(any(AuthLoginRequest.class), eq("admin"), any()))
                .willReturn(response("jwt.admin", "ADM2026001", List.of("admin"), List.of("*")));

        mockMvc.perform(post(ApiPaths.AUTH + "/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin.heqh",
                                  "password": "Ckqa@2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("jwt.admin"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.userCode").value("ADM2026001"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("admin"));
    }

    @Test
    void shouldRegisterStudentAndReturnToken() throws Exception {
        given(authService.registerStudent(any(AuthRegisterRequest.class)))
                .willReturn(response("jwt.student", "STU202605061200001", List.of("student"), List.of("course:read")));

        mockMvc.perform(post(ApiPaths.AUTH + "/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student.new",
                                  "displayName": "新同学",
                                  "password": "Ckqa@2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt.student"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("student"));
    }

    @Test
    void shouldReturnCurrentUserProfile() throws Exception {
        AuthenticatedUser currentUser = new AuthenticatedUser(
                1L,
                "ADM2026001",
                "admin.heqh",
                "何启航",
                List.of("admin"),
                List.of("*")
        );
        given(authService.getCurrentProfile(currentUser)).willReturn(currentUser.toProfile());

        mockMvc.perform(get(ApiPaths.AUTH + "/me")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, currentUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCode").value("ADM2026001"))
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/v1/user-avatars/default-user-avatar.svg"))
                .andExpect(jsonPath("$.data.permissions[0]").value("*"));
    }

    @Test
    void shouldUploadCurrentUserAvatar() throws Exception {
        AuthenticatedUser currentUser = new AuthenticatedUser(
                1L,
                "ADM2026001",
                "admin.heqh",
                "何启航",
                List.of("admin"),
                List.of("*")
        );
        AuthUserProfile updatedProfile = AuthUserProfile.builder()
                .id(1L)
                .userCode("ADM2026001")
                .username("admin.heqh")
                .displayName("何启航")
                .avatarUrl("/api/v1/user-avatars/user-avatar-1.png")
                .roles(List.of("admin"))
                .permissions(List.of("*"))
                .dataScope("全部课程")
                .build();
        MockMultipartFile avatar = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        given(authService.uploadCurrentUserAvatar(eq(currentUser), any())).willReturn(updatedProfile);

        mockMvc.perform(multipart(ApiPaths.AUTH + "/me/avatar")
                        .file(avatar)
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, currentUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/v1/user-avatars/user-avatar-1.png"));
    }

    @Test
    void shouldRejectWeakRegisterPassword() throws Exception {
        mockMvc.perform(post(ApiPaths.AUTH + "/student/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student.new",
                                  "displayName": "新同学",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));

        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void shouldUpdateCurrentUserDisplayName() throws Exception {
        AuthenticatedUser currentUser = new AuthenticatedUser(
                1L,
                "ADM2026001",
                "admin.heqh",
                "何启航",
                List.of("admin"),
                List.of("*")
        );
        AuthUserProfile updatedProfile = AuthUserProfile.builder()
                .id(1L)
                .userCode("ADM2026001")
                .username("admin.heqh")
                .displayName("何启航（已更新）")
                .avatarUrl("/api/v1/user-avatars/default-user-avatar.svg")
                .roles(List.of("admin"))
                .permissions(List.of("*"))
                .dataScope("全部课程")
                .email("admin@example.com")
                .phone("+8613800001111")
                .build();
        given(authService.updateCurrentProfile(
                eq(currentUser),
                eq("何启航（已更新）"),
                eq("admin@example.com"),
                eq("+8613800001111")
        )).willReturn(updatedProfile);

        mockMvc.perform(put(ApiPaths.AUTH + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, currentUser)
                        .content("""
                                {
                                  "displayName": "何启航（已更新）",
                                  "email": "admin@example.com",
                                  "phone": "+8613800001111"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("何启航（已更新）"))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.phone").value("+8613800001111"));
    }

    @Test
    void shouldRejectInvalidEmailOnUpdate() throws Exception {
        mockMvc.perform(put(ApiPaths.AUTH + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "何启航",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void shouldRejectInvalidPhoneOnUpdate() throws Exception {
        mockMvc.perform(put(ApiPaths.AUTH + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "何启航",
                                  "phone": "abc-1234"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void shouldRejectBlankDisplayNameOnUpdate() throws Exception {
        mockMvc.perform(put(ApiPaths.AUTH + "/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void shouldChangeCurrentUserPassword() throws Exception {
        AuthenticatedUser currentUser = new AuthenticatedUser(
                1L,
                "ADM2026001",
                "admin.heqh",
                "何启航",
                List.of("admin"),
                List.of("*")
        );

        mockMvc.perform(put(ApiPaths.AUTH + "/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, currentUser)
                        .content("""
                                {
                                  "oldPassword": "Ckqa@2026",
                                  "newPassword": "Ckqa@2027new"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        then(authService).should().changeCurrentPassword(currentUser, "Ckqa@2026", "Ckqa@2027new");
    }

    @Test
    void shouldRejectShortNewPasswordOnChange() throws Exception {
        mockMvc.perform(put(ApiPaths.AUTH + "/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldPassword": "Ckqa@2026",
                                  "newPassword": "abc"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
        then(authService).shouldHaveNoInteractions();
    }

    private AuthResponse response(
            String token,
            String userCode,
            List<String> roles,
            List<String> permissions
    ) {
        AuthUserProfile profile = AuthUserProfile.builder()
                .id(1L)
                .userCode(userCode)
                .username("admin.heqh")
                .displayName("何启航")
                .avatarUrl("/api/v1/user-avatars/default-user-avatar.svg")
                .roles(roles)
                .permissions(permissions)
                .dataScope("全部课程")
                .build();
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresAt(LocalDateTime.of(2026, 5, 6, 13, 0))
                .user(profile)
                .build();
    }
}
