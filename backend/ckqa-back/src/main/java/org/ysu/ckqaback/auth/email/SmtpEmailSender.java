package org.ysu.ckqaback.auth.email;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.config.CkqaEmailProperties;
import org.ysu.ckqaback.exception.BusinessException;

import jakarta.annotation.PostConstruct;
import java.util.Properties;

/**
 * SMTP 邮件发送器。
 *
 * <p>启用方式：把 {@code ckqa.email.mailer-type} 改为 {@code smtp} 并配置
 * {@code ckqa.email.smtp.host/port/username/password/ssl}。</p>
 */
@Component
@ConditionalOnProperty(prefix = "ckqa.email", name = "mailer-type", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final CkqaEmailProperties emailProperties;
    private JavaMailSender mailSender;

    @PostConstruct
    void init() {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        CkqaEmailProperties.Smtp smtp = emailProperties.getSmtp();
        impl.setHost(smtp.getHost());
        impl.setPort(smtp.getPort());
        impl.setUsername(smtp.getUsername());
        impl.setPassword(smtp.getPassword());
        impl.setDefaultEncoding("UTF-8");
        Properties javaMailProps = new Properties();
        if (smtp.isSsl()) {
            javaMailProps.put("mail.smtp.ssl.enable", "true");
            javaMailProps.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            javaMailProps.put("mail.smtp.starttls.enable", "true");
        }
        javaMailProps.put("mail.smtp.auth", "true");
        impl.setJavaMailProperties(javaMailProps);
        this.mailSender = impl;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String from = emailProperties.getFromAddress();
            String fromName = emailProperties.getFromName();
            if (fromName != null && !fromName.isBlank()) {
                message.setFrom(String.format("\"%s\" <%s>", fromName, from));
            } else {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.error("SMTP 发送邮件失败 to={}, subject={}, error={}", to, subject, exception.getMessage());
            throw new BusinessException(ApiResultCode.EMAIL_SEND_FAILED, HttpStatus.SERVICE_UNAVAILABLE,
                    "邮件发送失败：" + exception.getMessage());
        }
    }
}
