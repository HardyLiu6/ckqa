package org.ysu.ckqaback.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * 控制器层读取当前登录用户的轻量工具。
 */
public final class AuthContext {

    private AuthContext() {
    }

    public static String resolveUserCode(Jwt jwt, String fallbackUserCode) {
        String jwtUserCode = jwt == null ? null : jwt.getClaimAsString(AuthConstants.USER_CODE_CLAIM);
        if (StringUtils.hasText(jwtUserCode)) {
            return jwtUserCode;
        }
        return StringUtils.hasText(fallbackUserCode) ? fallbackUserCode.trim() : null;
    }

    public static String resolveUserCode(String fallbackUserCode) {
        return resolveUserCode(currentJwt(), fallbackUserCode);
    }

    public static AuthenticatedUser fromRequestOrJwt(HttpServletRequest request, Jwt jwt) {
        Object requestUser = request == null ? null : request.getAttribute(AuthConstants.REQUEST_USER_ATTRIBUTE);
        if (requestUser instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        return jwt == null ? null : AuthenticatedUser.fromJwt(jwt);
    }

    public static AuthenticatedUser fromRequestOrCurrentJwt(HttpServletRequest request) {
        return fromRequestOrJwt(request, currentJwt());
    }

    public static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }
}
