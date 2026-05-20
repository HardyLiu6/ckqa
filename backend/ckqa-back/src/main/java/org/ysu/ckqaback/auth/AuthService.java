package org.ysu.ckqaback.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.dto.AccountAvailabilityResponse;
import org.ysu.ckqaback.auth.dto.AuthLoginRequest;
import org.ysu.ckqaback.auth.dto.AuthRegisterRequest;
import org.ysu.ckqaback.auth.dto.AuthResponse;
import org.ysu.ckqaback.auth.dto.AuthUserProfile;
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
import org.springframework.web.multipart.MultipartFile;

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
    private final UserAvatarService userAvatarService;
    private final LoginRateLimiter loginRateLimiter;
    private final TurnstileVerifier turnstileVerifier;
    private final EmailCodeService emailCodeService;

    public AuthResponse loginForAudience(AuthLoginRequest request, String audience) {
        return loginForAudience(request, audience, null);
    }

    /**
     * 密码登录入口（含限频 + 人机验证）。
     *
     * @param request   登录请求
     * @param audience  admin / student
     * @param clientIp  客户端 IP，用于 turnstile 与限频 bucket 拼装；可空
     */
    public AuthResponse loginForAudience(AuthLoginRequest request, String audience, String clientIp) {
        // 人机验证（disabled 时直接放行）
        turnstileVerifier.verify(request.getTurnstileToken(), clientIp);
        String bucket = bucketKey(request.getUsername(), clientIp, audience);
        loginRateLimiter.ensureNotLocked(bucket);

        Users user = findUserForLogin(request.getUsername());
        if (user == null || !"active".equals(user.getStatus()) || !passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(bucket);
            throw new BusinessException(ApiResultCode.AUTH_INVALID, HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        List<String> roles = usersService.getRoleCodes(user.getId());
        if (!canLoginAudience(roles, audience)) {
            loginRateLimiter.recordFailure(bucket);
            throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, audienceErrorMessage(audience));
        }
        loginRateLimiter.recordSuccess(bucket);
        user.setLastLoginAt(LocalDateTime.now());
        usersService.updateById(user);
        return issueResponse(user, roles);
    }

    /**
     * 邮箱验证码登录。
     *
     * <p>{@code audience} 决定可登录的学生 / 教师 / 管理员；调用入口同时校验该用户是否被允许使用此 audience，
     * 防止学生邮箱拿管理员入口的 token。</p>
     */
    public AuthResponse loginByEmailCode(String email, String code, String turnstileToken, String clientIp, String audience) {
        turnstileVerifier.verify(turnstileToken, clientIp);
        String bucket = bucketKey(email, clientIp, "email-" + audience);
        loginRateLimiter.ensureNotLocked(bucket);

        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        Users user = usersService.getOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getEmail, email.trim().toLowerCase())
                .eq(Users::getIsDeleted, false));
        if (user == null || !"active".equals(user.getStatus())) {
            loginRateLimiter.recordFailure(bucket);
            throw new BusinessException(ApiResultCode.EMAIL_NOT_REGISTERED, HttpStatus.UNAUTHORIZED);
        }
        try {
            emailCodeService.verifyAndConsume(email, code, EmailCodeService.SCENE_LOGIN);
        } catch (BusinessException ex) {
            loginRateLimiter.recordFailure(bucket);
            throw ex;
        }
        List<String> roles = usersService.getRoleCodes(user.getId());
        if (!canLoginAudience(roles, audience)) {
            loginRateLimiter.recordFailure(bucket);
            throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, audienceErrorMessage(audience));
        }
        loginRateLimiter.recordSuccess(bucket);
        user.setLastLoginAt(LocalDateTime.now());
        usersService.updateById(user);
        return issueResponse(user, roles);
    }

    /**
     * 通过邮箱发送验证码（限频 + 人机验证）。
     *
     * <p>scene 决定 Redis key 与邮件文案：
     * <ul>
     *   <li>{@link EmailCodeService#SCENE_LOGIN}：要求邮箱已绑定 active 用户，避免被滥用作邮件群发</li>
     *   <li>{@link EmailCodeService#SCENE_REGISTER}：要求邮箱当前未被占用，避免重复绑定</li>
     *   <li>{@link EmailCodeService#SCENE_RESET_PASSWORD}：要求邮箱已绑定 active 用户</li>
     * </ul></p>
     */
    public void sendEmailLoginCode(String email, String turnstileToken, String clientIp) {
        sendEmailCode(email, EmailCodeService.SCENE_LOGIN, turnstileToken, clientIp);
    }

    public void sendEmailCode(String email, String scene, String turnstileToken, String clientIp) {
        turnstileVerifier.verify(turnstileToken, clientIp);
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        String normalized = email.trim().toLowerCase();
        String normalizedScene = StringUtils.hasText(scene) ? scene.trim().toLowerCase() : EmailCodeService.SCENE_LOGIN;

        Users existing = usersService.getOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getEmail, normalized)
                .eq(Users::getIsDeleted, false));

        switch (normalizedScene) {
            case EmailCodeService.SCENE_REGISTER -> {
                if (existing != null) {
                    throw new BusinessException(ApiResultCode.EMAIL_ALREADY_BOUND, HttpStatus.CONFLICT);
                }
            }
            case EmailCodeService.SCENE_RESET_PASSWORD, EmailCodeService.SCENE_LOGIN -> {
                if (existing == null || !"active".equals(existing.getStatus())) {
                    throw new BusinessException(ApiResultCode.EMAIL_NOT_REGISTERED, HttpStatus.NOT_FOUND);
                }
            }
            default -> throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "不支持的验证码场景");
        }

        emailCodeService.sendCode(normalized, normalizedScene);
    }

    private String bucketKey(String identity, String clientIp, String audience) {
        String safeId = StringUtils.hasText(identity) ? identity.trim().toLowerCase() : "anonymous";
        String safeIp = StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";
        return safeId + "|" + safeIp + "|" + audience;
    }

    /** 从 HttpServletRequest 解析 client IP，用于限频与 turnstile remoteip 字段。 */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    @Transactional
    public AuthResponse registerStudent(AuthRegisterRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (usersService.count(new LambdaQueryWrapper<Users>().eq(Users::getUsername, username)) > 0) {
            throw new BusinessException(ApiResultCode.USERNAME_EXISTS, HttpStatus.CONFLICT);
        }
        Roles studentRole = rolesService.getOne(new LambdaQueryWrapper<Roles>().eq(Roles::getRoleCode, "student"));
        if (studentRole == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.CONFLICT, "缺少student角色");
        }

        // 注册时若提供邮箱，需要同步校验验证码并确认邮箱未被占用
        String boundEmail = null;
        boolean emailVerified = false;
        if (StringUtils.hasText(request.getEmail())) {
            boundEmail = request.getEmail().trim().toLowerCase();
            if (!StringUtils.hasText(request.getEmailCode())) {
                throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "邮箱已填写，请同时输入邮箱验证码");
            }
            long emailUsed = usersService.count(new LambdaQueryWrapper<Users>()
                    .eq(Users::getEmail, boundEmail)
                    .eq(Users::getIsDeleted, false));
            if (emailUsed > 0) {
                throw new BusinessException(ApiResultCode.EMAIL_ALREADY_BOUND, HttpStatus.CONFLICT);
            }
            emailCodeService.verifyAndConsume(boundEmail, request.getEmailCode(), EmailCodeService.SCENE_REGISTER);
            emailVerified = true;
        }

        Users user = new Users();
        user.setUserCode(nextStudentCode());
        user.setUsername(username);
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordService.hash(request.getPassword()));
        user.setStatus("active");
        if (boundEmail != null) {
            user.setEmail(boundEmail);
            if (emailVerified) {
                user.setEmailVerifiedAt(LocalDateTime.now());
            }
        }
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

    /**
     * 通过邮箱验证码重置密码。
     *
     * <p>校验流程：
     * <ol>
     *   <li>邮箱必须已绑定到 active 用户</li>
     *   <li>验证码必须与 reset-password 场景匹配（一次性消费）</li>
     *   <li>新密码满足强度规则（≥ 8 字符、含字母与数字）</li>
     * </ol>
     * 成功后立即写入新的 password_hash。</p>
     */
    @Transactional
    public void resetPasswordByEmail(String email, String code, String newPassword) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "新密码不能为空");
        }
        if (newPassword.length() < 8 || newPassword.length() > 72) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "新密码长度需在 8-72 字符之间");
        }
        if (!newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "新密码需同时包含字母与数字");
        }

        String normalizedEmail = email.trim().toLowerCase();
        Users user = usersService.getOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getEmail, normalizedEmail)
                .eq(Users::getIsDeleted, false));
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ApiResultCode.EMAIL_NOT_REGISTERED, HttpStatus.NOT_FOUND);
        }
        emailCodeService.verifyAndConsume(normalizedEmail, code, EmailCodeService.SCENE_RESET_PASSWORD);
        user.setPasswordHash(passwordService.hash(newPassword));
        usersService.updateById(user);
    }

    /**
     * 注册前的账号 / 邮箱占用检查。
     *
     * <p>{@code field} 取值：username / email；其他取值返回 BAD_REQUEST。
     * 该接口故意不暴露具体冲突账号的展示名，避免账号枚举攻击。</p>
     */
    public AccountAvailabilityResponse checkAvailability(String field, String value) {
        if (!StringUtils.hasText(field) || !StringUtils.hasText(value)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "请提供 field 与 value 参数");
        }
        String normalizedField = field.trim().toLowerCase();
        String normalizedValue = value.trim();
        long taken;
        switch (normalizedField) {
            case "username" -> taken = usersService.count(new LambdaQueryWrapper<Users>()
                    .eq(Users::getUsername, normalizedValue)
                    .eq(Users::getIsDeleted, false));
            case "email" -> taken = usersService.count(new LambdaQueryWrapper<Users>()
                    .eq(Users::getEmail, normalizedValue.toLowerCase())
                    .eq(Users::getIsDeleted, false));
            default -> throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "field 仅支持 username 或 email");
        }
        boolean available = taken == 0;
        String message = available
                ? ("username".equals(normalizedField) ? "账号可用" : "邮箱可用")
                : ("username".equals(normalizedField) ? "账号已被占用" : "邮箱已被绑定");
        return AccountAvailabilityResponse.builder()
                .available(available)
                .field(normalizedField)
                .message(message)
                .build();
    }

    public AuthUserProfile getCurrentProfile(AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        Users user = usersService.getById(currentUser.id());
        if (user == null) {
            return currentUser.toProfile();
        }
        return toProfile(user, currentUser.roles(), currentUser.permissions());
    }

    public AuthUserProfile uploadCurrentUserAvatar(AuthenticatedUser currentUser, MultipartFile file) {
        if (currentUser == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        Users user = usersService.getRequiredById(currentUser.id());
        Users updatedUser = userAvatarService.storeForUser(user, file);
        return toProfile(updatedUser, currentUser.roles(), currentUser.permissions());
    }

    /**
     * 个人中心：更新当前用户的显示名 / 邮箱 / 手机号。
     * <p>三字段全部按「null=不动 / 空串=清空 / 有值=更新」语义处理。
     * 用户代码、用户名、角色、权限由管理员维护。</p>
     */
    @Transactional
    public AuthUserProfile updateCurrentProfile(
            AuthenticatedUser currentUser,
            String displayName,
            String email,
            String phone
    ) {
        if (currentUser == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(displayName)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "显示名不能为空");
        }
        String trimmedName = displayName.trim();
        if (trimmedName.length() > 128) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "显示名长度不能超过 128 字符");
        }
        Users user = usersService.getRequiredById(currentUser.id());
        user.setDisplayName(trimmedName);
        if (email != null) {
            String trimmedEmail = email.trim();
            user.setEmail(trimmedEmail.isEmpty() ? null : trimmedEmail);
        }
        if (phone != null) {
            String trimmedPhone = phone.trim();
            user.setPhone(trimmedPhone.isEmpty() ? null : trimmedPhone);
        }
        usersService.updateById(user);
        return toProfile(user, currentUser.roles(), currentUser.permissions());
    }

    /**
     * 个人中心：修改当前用户密码。
     * <p>校验：旧密码与库内 hash 匹配；新密码与旧密码不同；新密码满足强度规则
     * （≥ 8 字符 + 至少一个字母 + 至少一个数字）。其它字段未触碰。</p>
     */
    @Transactional
    public void changeCurrentPassword(AuthenticatedUser currentUser, String oldPassword, String newPassword) {
        if (currentUser == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(oldPassword)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "原密码不能为空");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "新密码不能为空");
        }
        if (newPassword.length() < 8 || newPassword.length() > 64) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "新密码长度需在 8-64 字符之间");
        }
        if (newPassword.equals(oldPassword)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }
        if (!newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "新密码需同时包含字母与数字");
        }
        Users user = usersService.getRequiredById(currentUser.id());
        if (!passwordService.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ApiResultCode.AUTH_INVALID, HttpStatus.UNAUTHORIZED, "原密码不正确");
        }
        user.setPasswordHash(passwordService.hash(newPassword));
        usersService.updateById(user);
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
                .user(toProfile(user, roles, permissions))
                .build();
    }

    private AuthUserProfile toProfile(Users user, List<String> roles, List<String> permissions) {
        return AuthUserProfile.builder()
                .id(user.getId())
                .userCode(user.getUserCode())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(UserAvatarService.resolveResponseAvatarUrl(user))
                .roles(roles == null ? List.of() : roles)
                .permissions(permissions == null ? List.of() : permissions)
                .dataScope(resolveDataScope(roles))
                .email(user.getEmail())
                .phone(user.getPhone())
                .lastLoginAt(user.getLastLoginAt())
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
                    "material:write",
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

    private String resolveDataScope(List<String> roles) {
        if (roles == null) {
            return "授权课程";
        }
        if (roles.contains("admin")) {
            return "全部课程";
        }
        if (roles.contains("teacher")) {
            return "授权课程";
        }
        return "已加入课程";
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
