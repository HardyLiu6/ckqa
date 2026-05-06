package org.ysu.ckqaback.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.dto.AuthLoginRequest;
import org.ysu.ckqaback.auth.dto.AuthRegisterRequest;
import org.ysu.ckqaback.auth.dto.AuthResponse;
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
        service = new AuthService(
                usersService,
                rolesService,
                userRolesService,
                authIdentitiesService,
                passwordService,
                new JwtTokenService(jwtProperties),
                mock(UserAvatarService.class)
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
