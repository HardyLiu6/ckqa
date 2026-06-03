package org.ysu.ckqaback.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 课程问答语义相关性闸门配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.qa-domain-guard")
public class QaDomainGuardProperties {

    /** 闸门总开关；关闭后所有问题放行。 */
    private boolean enabled = true;

    /** 余弦相似度低于该阈值才判为无关；0.25 为 2026-06-03 实测校准值（OS 课 text-embedding-v4：切题下沿≈0.31、无关上沿≈0.19 的间隙中点，见设计稿 §8.1）。 */
    private double outOfScopeThreshold = 0.25D;
}
