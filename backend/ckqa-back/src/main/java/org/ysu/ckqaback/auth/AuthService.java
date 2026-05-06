package org.ysu.ckqaback.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.dto.AuthLoginRequest;
import org.ysu.ckqaback.auth.dto.AuthRegisterRequest;
import org.ysu.ckqaback.auth.dto.AuthResponse;
import org.ysu.ckqaback.auth.dto.AuthUserProfile;
import org.ysu.ckqaback.entity.AuthIdentities;
import org.ysu.ckqaback.entity.Roles;
import org.ysu.ckqaback.entity.UserRoles;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.AuthIdentitiesService;
import org.ysu.ckqaback.service.RolesService;
import org.ysu.ckqaback.service.UserRolesService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地账号登录、学生注册和 JWT 签发服务。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final DateTimeFormatter USER_CODE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final UsersService usersService;
    private final RolesService rolesService;
    private final UserRolesService userRolesService;
    private final AuthIdentitiesService authIdentitiesService;
    private final PasswordService passwordService;
    private final JwtTokenService jwtTokenService;

    public AuthResponse loginForAudience(AuthLoginRequest request, String audience) {
        Users user = findUserForLogin(request.getUsername());
        if (user == null || !"active".equals(user.getStatus()) || !passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ApiResultCode.AUTH_INVALID, HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        List<String> roles = usersService.getRoleCodes(user.getId());
        if (!canLoginAudience(roles, audience)) {
            throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, audienceErrorMessage(audience));
        }
        user.setLastLoginAt(LocalDateTime.now());
        usersService.updateById(user);
        return issueResponse(user, roles);
    }

    @Transactional
    public AuthResponse registerStudent(AuthRegisterRequest request) {
        if (usersService.count(new LambdaQueryWrapper<Users>().eq(Users::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ApiResultCode.USERNAME_EXISTS, HttpStatus.CONFLICT);
        }
        Roles studentRole = rolesService.getOne(new LambdaQueryWrapper<Roles>().eq(Roles::getRoleCode, "student"));
        if (studentRole == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.CONFLICT, "缺少student角色");
        }

        Users user = new Users();
        user.setUserCode(nextStudentCode());
        user.setUsername(request.getUsername().trim());
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordService.hash(request.getPassword()));
        user.setStatus("active");
        usersService.save(user);

        UserRoles userRole = new UserRoles();
        userRole.setUserId(user.getId());
        userRole.setRoleId(studentRole.getId());
        userRolesService.save(userRole);

        AuthIdentities identity = new AuthIdentities();
        identity.setUserId(user.getId());
        identity.setProvider("local");
        identity.setProviderUserId(user.getUsername());
        identity.setIdentityKey(user.getUserCode());
        identity.setCredentialMeta("{\"credential\":\"password\"}");
        authIdentitiesService.save(identity);

        return issueResponse(user, List.of("student"));
    }

    public AuthUserProfile getCurrentProfile(AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        return currentUser.toProfile();
    }

    private Users findUserForLogin(String usernameOrCode) {
        String identity = usernameOrCode == null ? "" : usernameOrCode.trim();
        if (!StringUtils.hasText(identity)) {
            return null;
        }
        return usersService.getOne(new LambdaQueryWrapper<Users>()
                .and(wrapper -> wrapper
                        .eq(Users::getUsername, identity)
                        .or()
                        .eq(Users::getUserCode, identity))
                .eq(Users::getIsDeleted, false));
    }

    private AuthResponse issueResponse(Users user, List<String> roles) {
        List<String> permissions = resolvePermissions(user.getId(), roles);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getUserCode(),
                user.getUsername(),
                user.getDisplayName(),
                roles == null ? List.of() : roles,
                permissions
        );
        JwtTokenService.IssuedToken token = jwtTokenService.issue(authenticatedUser);
        return AuthResponse.builder()
                .accessToken(token.accessToken())
                .tokenType("Bearer")
                .expiresAt(token.expiresAt())
                .user(authenticatedUser.toProfile())
                .build();
    }

    private List<String> resolvePermissions(Long userId, List<String> roles) {
        Set<String> permissions = new LinkedHashSet<>();
        if (roles != null && roles.contains("admin")) {
            permissions.add("*");
            return new ArrayList<>(permissions);
        }
        permissions.addAll(usersService.getPermissionCodes(userId));
        if (roles != null && roles.contains("teacher")) {
            permissions.addAll(List.of(
                    "course:read",
                    "material:read",
                    "material:parse",
                    "material:export",
                    "kb:read",
                    "kb:write",
                    "kb:index",
                    "kb:activate",
                    "qa:read",
                    "qa:log:read",
                    "membership:read",
                    "membership:write",
                    "system:read"
            ));
        }
        if (roles != null && roles.contains("student")) {
            permissions.addAll(List.of("course:read", "course.query"));
        }
        return new ArrayList<>(permissions);
    }

    private boolean canLoginAudience(List<String> roles, String audience) {
        if ("student".equals(audience)) {
            return roles.contains("student");
        }
        return roles.contains("admin") || roles.contains("teacher");
    }

    private String audienceErrorMessage(String audience) {
        return "student".equals(audience) ? "当前账号不能进入学生端" : "当前账号不能进入管理员端";
    }

    private String nextStudentCode() {
        String prefix = "STU";
        String code;
        do {
            code = prefix + LocalDateTime.now().format(USER_CODE_FORMAT);
        } while (usersService.count(new LambdaQueryWrapper<Users>().eq(Users::getUserCode, code)) > 0);
        return code;
    }
}
