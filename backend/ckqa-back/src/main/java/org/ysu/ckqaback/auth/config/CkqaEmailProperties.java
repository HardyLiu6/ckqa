package org.ysu.ckqaback.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件发送配置。
 *
 * <p>支持两种 mailerType：
 * <ul>
 *   <li>{@code log}（默认）：把验证码打印到后端日志，开发态调试不依赖外部 SMTP</li>
 *   <li>{@code smtp}：使用 spring-boot-starter-mail 发送真实邮件，需配置 host/port/username/password</li>
 * </ul></p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.email")
public class CkqaEmailProperties {

    /** mailer 类型：log / smtp。 */
    private String mailerType = "log";

    /** 发件人邮箱地址。 */
    private String fromAddress = "noreply@ckqa.local";

    /** 发件人显示名。 */
    private String fromName = "智课问答";

    private final Smtp smtp = new Smtp();

    @Getter
    @Setter
    public static class Smtp {
        private String host = "";
        private int port = 465;
        private String username = "";
        private String password = "";
        /** 是否启用 SSL（465 端口默认 true，587 端口请改为 false 并启用 STARTTLS）。 */
        private boolean ssl = true;
    }
}
