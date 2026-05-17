package org.ysu.ckqaback.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 日志兜底邮件发送器。
 *
 * <p>开发态默认实现：把邮件主题与正文打到后端日志，便于调试。
 * 上线前请改 {@code ckqa.email.mailer-type=smtp} 接入真实 SMTP。</p>
 */
@Component
@ConditionalOnProperty(prefix = "ckqa.email", name = "mailer-type", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info(
                "\n========== [DEV EMAIL] ==========\nTo: {}\nSubject: {}\nBody:\n{}\n==================================",
                to, subject, body
        );
    }
}
