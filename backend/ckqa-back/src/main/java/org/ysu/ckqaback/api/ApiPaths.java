package org.ysu.ckqaback.api;

/**
 * API 路由常量定义。
 * <p>
 * 所有控制器统一通过该类管理基础路径，避免在控制器上散落硬编码字符串。
 * </p>
 */
public final class ApiPaths {

    public static final String API_V1 = "/api/v1";
    public static final String AUTH_IDENTITIES = API_V1 + "/auth-identities";
    public static final String AUTHORIZATION_AUDIT_LOGS = API_V1 + "/authorization-audit-logs";
    public static final String COURSE_MEMBERSHIP_EVENTS = API_V1 + "/course-membership-events";
    public static final String COURSE_MEMBERSHIPS = API_V1 + "/course-memberships";
    public static final String COURSES = API_V1 + "/courses";
    public static final String INDEX_ARTIFACTS = API_V1 + "/index-artifacts";
    public static final String INDEX_RUNS = API_V1 + "/index-runs";
    public static final String KB_DOCUMENTS = API_V1 + "/kb-documents";
    public static final String KNOWLEDGE_BASES = API_V1 + "/knowledge-bases";
    public static final String PARSE_LOGS = API_V1 + "/parse-logs";
    public static final String PARSE_RESULTS = API_V1 + "/parse-results";
    public static final String PDF_FILES = API_V1 + "/pdf-files";
    public static final String PERMISSIONS = API_V1 + "/permissions";
    public static final String QA_MESSAGES = API_V1 + "/qa-messages";
    public static final String QA_RETRIEVAL_HITS = API_V1 + "/qa-retrieval-hits";
    public static final String QA_RETRIEVAL_LOGS = API_V1 + "/qa-retrieval-logs";
    public static final String QA_SESSIONS = API_V1 + "/qa-sessions";
    public static final String ROLE_PERMISSIONS = API_V1 + "/role-permissions";
    public static final String ROLES = API_V1 + "/roles";
    public static final String USER_ROLES = API_V1 + "/user-roles";
    public static final String USERS = API_V1 + "/users";

    private ApiPaths() {
    }
}
