package org.ysu.ckqaback.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.ysu.ckqaback.auth.dto.AuthUserProfile;

import java.util.List;

/**
 * JWT 中携带的当前用户快照。
 */
public record AuthenticatedUser(
        Long id,
        String userCode,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions
) {

    public static AuthenticatedUser fromJwt(Jwt jwt) {
        return new AuthenticatedUser(
                jwt.getClaim(AuthConstants.USER_ID_CLAIM),
                jwt.getClaimAsString(AuthConstants.USER_CODE_CLAIM),
                jwt.getClaimAsString(AuthConstants.USERNAME_CLAIM),
                jwt.getClaimAsString(AuthConstants.DISPLAY_NAME_CLAIM),
                claimList(jwt, AuthConstants.ROLES_CLAIM),
                claimList(jwt, AuthConstants.PERMISSIONS_CLAIM)
        );
    }

    public AuthUserProfile toProfile() {
        return AuthUserProfile.builder()
                .id(id)
                .userCode(userCode)
                .username(username)
                .displayName(displayName)
                .roles(roles == null ? List.of() : roles)
                .permissions(permissions == null ? List.of() : permissions)
                .dataScope(resolveDataScope(roles))
                .build();
    }

    private static List<String> claimList(Jwt jwt, String claimName) {
        List<String> value = jwt.getClaimAsStringList(claimName);
        return value == null ? List.of() : value;
    }

    private static String resolveDataScope(List<String> roles) {
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
}
