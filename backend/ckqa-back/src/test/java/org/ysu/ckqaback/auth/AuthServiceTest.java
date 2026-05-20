package org.ysu.ckqaback.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.dto.AuthLoginRequest;
import org.ysu.ckqaback.auth.dto.AuthRegisterRequest;
import org.ysu.ckqaback.auth.dto.AuthResponse;
import org.ysu.ckqaback.auth.email.EmailCodeService;
import org.ysu.ckqaback.auth.security.LoginRateLimiter;
import org.ysu.ckqaback.auth.security.TurnstileVerifier;
import org.ysu.ckqaback.entity.AuthIdentities;
import org.ysu.ckqaback.entity.Roles;
import org.ysu.ckqaback.entity.UserRoles;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.AuthIdentitiesService;
import org.ysu.ckqaback.service.RolesService;
import org.ysu.ckqaback.service.UserRolesService;
import org.ysu.ckqaback.service.UsersService;
import org.ysu.ckqaback.user.UserAvatarService;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UsersService usersService;
    private RolesService rolesService;
    private UserRolesService userRolesService;
    private AuthIdentitiesService authIdentitiesService;
    private PasswordService passwordService;
    private LoginRateLimiter loginRateLimiter;
    private TurnstileVerifier turnstileVerifier;
    private EmailCodeService emailCodeService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        usersService = mock(UsersService.class);
        rolesService = mock(RolesService.class);
        userRolesService = mock(UserRolesService.class);
        authIdentitiesService = mock(AuthIdentitiesService.class);
        passwordService = new PasswordService();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-that-is-long-enough-for-hmac");
        jwtProperties.setIssuer("ckqa-test");
        jwtProperties.setTtl(Duration.ofMinutes(30));
        // 限频 / Turnstile / 邮件相关 mock：单测里默认放行 + 不发码
        loginRateLimiter = mock(LoginRateLimiter.class);
        turnstileVerifier = mock(TurnstileVerifier.class);
        emailCodeService = mock(EmailCodeService.class);
        service = new AuthService(
                usersService,
                rolesService,
                userRolesService,
                authIdentitiesService,
                passwordService,
                new JwtTokenService(jwtProperties),
                mock(UserAvatarService.class),
                loginRateLimiter,
                turnstileVerifier,
                emailCodeService
        );
    }

    @Test
    void shouldLoginAdminAudienceWithAdminRole() {
        Users admin = user(1L, "ADM2026001", "admin.heqh", "何启航", passwordService.hash("Ckqa@2026"));
        when(usersService.getOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
        when(usersService.getRoleCodes(1L)).thenReturn(List.of("admin"));
        when(usersService.getPermissionCodes(1L)).thenReturn(List.of("*"));

        AuthResponse response = service.loginForAudience(login("admin.heqh", "Ckqa@2026"), "admin");

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getUserCode()).isEqualTo("ADM2026001");
        assertThat(response.getUser().getRoles()).containsExactly("admin");
        verify(usersService).updateById(admin);
    }

    @Test
    void shouldRejectStudentOnAdminAudience() {
        Users student = user(2L, "STU2026001", "student.zhouzh", "周子涵", passwordService.hash("Ckqa@2026"));
        when(usersService.getOne(any(LambdaQueryWrapper.class))).thenReturn(student);
        when(usersService.getRoleCodes(2L)).thenReturn(List.of("student"));
        when(usersService.getPermissionCodes(2L)).thenReturn(List.of("course:read"));

        assertThatThrownBy(() -> service.loginForAudience(login("student.zhouzh", "Ckqa@2026"), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前账号不能进入管理员端");
    }

    @Test
    void shouldRegisterStudentWithHashedPasswordAndRole() {
        Roles role = new Roles();
        role.setId(8L);
        role.setRoleCode("student");
        when(usersService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(rolesService.getOne(any(LambdaQueryWrapper.class))).thenReturn(role);
        when(usersService.save(any(Users.class))).thenAnswer(invocation -> {
            Users saved = invocation.getArgument(0);
            saved.setId(99L);
            return true;
        });
        when(usersService.getRoleCodes(99L)).thenReturn(List.of("student"));
        when(usersService.getPermissionCodes(99L)).thenReturn(List.of("course:read"));

        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername("student.new");
        request.setDisplayName("新同学");
        request.setPassword("Ckqa@2026");
        AuthResponse response = service.registerStudent(request);

        assertThat(response.getUser().getUserCode()).startsWith("STU");
        assertThat(response.getUser().getRoles()).containsExactly("student");
        verify(usersService).save(any(Users.class));
        verify(userRolesService).save(any(UserRoles.class));
        verify(authIdentitiesService).save(any(AuthIdentities.class));
    }

    @Test
    void shouldBindEmailWhenRegisterWithVerifiedCode() {
        Roles role = new Roles();
        role.setId(8L);
        role.setRoleCode("student");
        // 第一次 count 检查 username（0），第二次 count 检查 email 是否被占用（0）
        when(usersService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(rolesService.getOne(any(LambdaQueryWrapper.class))).thenReturn(role);
        when(usersService.save(any(Users.class))).thenAnswer(invocation -> {
            Users saved = invocation.getArgument(0);
            saved.setId(101L);
            return true;
        });
        when(usersService.getRoleCodes(101L)).thenReturn(List.of("student"));
        when(usersService.getPermissionCodes(101L)).thenReturn(List.of("course:read"));

        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername("student.new");
        request.setDisplayName("新同学");
        request.setPassword("Ckqa@2026");
        request.setEmail(" Demo@Example.com ");
        request.setEmailCode("123456");

        AuthResponse response = service.registerStudent(request);

        // 邮箱验证码必须按 register scene 一次性消费
        verify(emailCodeService).verifyAndConsume("demo@example.com", "123456", EmailCodeService.SCENE_REGISTER);
        assertThat(response.getUser().getEmail()).isEqualTo("demo@example.com");
    }

    @Test
    void shouldRejectRegisterWhenEmailAlreadyBound() {
        Roles role = new Roles();
        role.setId(8L);
        role.setRoleCode("student");
        when(rolesService.getOne(any(LambdaQueryWrapper.class))).thenReturn(role);
        // 第一次 username count = 0, 第二次 email count = 1
        when(usersService.count(any(LambdaQueryWrapper.class))).thenReturn(0L, 1L);

        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername("student.new");
        request.setDisplayName("新同学");
        request.setPassword("Ckqa@2026");
        request.setEmail("dup@example.com");
        request.setEmailCode("123456");

        assertThatThrownBy(() -> service.registerStudent(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱");
    }

    @Test
    void shouldRequireCodeWhenEmailProvidedDuringRegister() {
        Roles role = new Roles();
        role.setId(8L);
        role.setRoleCode("student");
        when(rolesService.getOne(any(LambdaQueryWrapper.class))).thenReturn(role);
        when(usersService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);

        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setUsername("student.new");
        request.setDisplayName("新同学");
        request.setPassword("Ckqa@2026");
        request.setEmail("foo@example.com");
        // 故意不提供 emailCode
        assertThatThrownBy(() -> service.registerStudent(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码");
    }

    @Test
    void shouldResetPasswordByVerifiedEmailCode() {
        Users existing = user(7L, "STU2026010", "student.bob", "鲍勃", passwordService.hash("OldPass123"));
        existing.setEmail("bob@example.com");
        when(usersService.getOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        service.resetPasswordByEmail("Bob@Example.com", "654321", "FreshPass2026");

        verify(emailCodeService).verifyAndConsume("bob@example.com", "654321", EmailCodeService.SCENE_RESET_PASSWORD);
        assertThat(passwordService.matches("FreshPass2026", existing.getPasswordHash())).isTrue();
        verify(usersService).updateById(existing);
    }

    @Test
    void shouldRejectResetWhenEmailNotRegistered() {
        when(usersService.getOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.resetPasswordByEmail("ghost@example.com", "111111", "FreshPass2026"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未绑定");
    }

    @Test
    void shouldRejectWeakPasswordOnReset() {
        Users existing = user(7L, "STU2026010", "student.bob", "鲍勃", passwordService.hash("OldPass123"));
        existing.setEmail("bob@example.com");
        when(usersService.getOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        // 不含字母
        assertThatThrownBy(() -> service.resetPasswordByEmail("bob@example.com", "654321", "1234567890"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字母");
    }

    @Test
    void shouldReturnAvailabilityForUnusedUsername() {
        when(usersService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        var result = service.checkAvailability("username", "fresh.user");
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getField()).isEqualTo("username");
    }

    @Test
    void shouldReturnUnavailableForTakenEmail() {
        when(usersService.count(any(LambdaQueryWrapper.class))).thenReturn(1L);
        var result = service.checkAvailability("email", "taken@example.com");
        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getField()).isEqualTo("email");
    }

    @Test
    void shouldRejectAvailabilityWithUnknownField() {
        assertThatThrownBy(() -> service.checkAvailability("phone", "13800001111"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("field");
    }

    private AuthLoginRequest login(String username, String password) {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private Users user(Long id, String userCode, String username, String displayName, String passwordHash) {
        Users user = new Users();
        user.setId(id);
        user.setUserCode(userCode);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordHash);
        user.setStatus("active");
        return user;
    }
}
