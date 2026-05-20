package org.ysu.ckqaback.auth.dto;

/**
 * 学生端邮箱验证码登录请求。
 *
 * <p>当前与 {@link EmailLoginRequest} 字段相同；保留独立类型是为了将来按学生 audience
 * 单独扩展（例如新增 grade、班级绑定校验等）时不影响管理员端契约。</p>
 */
public class EmailLoginStudentRequest extends EmailLoginRequest {
}
