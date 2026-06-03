package org.ysu.ckqaback.course.routing;

/**
 * 单课程语义相关性判定端口。
 * <p>{@code evaluated=false} 表示未能算出有效相似度（未启用 / 课程缺失 / 服务故障），调用方据此 fail-open。</p>
 */
public interface CourseScopeRelevanceProvider {

    ScopeRelevance evaluateScopeRelevance(String courseId, String question);

    record ScopeRelevance(boolean evaluated, double confidence) {

        public static ScopeRelevance notEvaluated() {
            return new ScopeRelevance(false, 0d);
        }

        public static ScopeRelevance evaluated(double confidence) {
            return new ScopeRelevance(true, confidence);
        }
    }
}
