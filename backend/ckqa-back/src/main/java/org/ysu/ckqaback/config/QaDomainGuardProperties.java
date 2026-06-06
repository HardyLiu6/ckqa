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

    /** 余弦相似度低于该阈值才判为无关；0.33 小幅高于“二叉树”边界分数，同时保留“死锁”短定义余量。 */
    private double outOfScopeThreshold = 0.33D;
}
