package org.ysu.ckqaback.auth.email;

/**
 * 邮件发送抽象。
 *
 * <p>两个实现按 {@code ckqa.email.mailer-type} 切换：
 * <ul>
 *   <li>{@link LogEmailSender}：mailer-type=log（默认），把验证码打到日志</li>
 *   <li>{@link SmtpEmailSender}：mailer-type=smtp，通过 spring-boot-starter-mail 真实发送</li>
 * </ul></p>
 */
public interface EmailSender {

    /**
     * 发送一封 plain text 邮件。
     *
     * @param to       收件人邮箱地址
     * @param subject  邮件主题
     * @param body     邮件正文（plain text）
     */
    void send(String to, String subject, String body);
}
