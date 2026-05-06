package org.ysu.ckqaback.auth;

/**
 * 认证链路常量。
 */
public final class AuthConstants {

    public static final String REQUEST_USER_ATTRIBUTE = "ckqa.authenticatedUser";
    public static final String USER_ID_CLAIM = "uid";
    public static final String USER_CODE_CLAIM = "userCode";
    public static final String USERNAME_CLAIM = "username";
    public static final String DISPLAY_NAME_CLAIM = "displayName";
    public static final String ROLES_CLAIM = "roles";
    public static final String PERMISSIONS_CLAIM = "permissions";

    private AuthConstants() {
    }
}
