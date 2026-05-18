package org.ysu.ckqaback.qa.routing;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationRequest;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 低成本问答模式路由器。
 * <p>
 * 第一版采用条件路由 + 路由参考词面，避免为智能推荐额外增加在线 LLM 成本。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class QaModeRoutingService {

    private static final String STRATEGY = "rule_semantic_v1";
    private static final List<String> MODES = List.of("basic", "local", "global", "drift", "hybrid_v0");
    private static final Pattern MATERIAL_PATTERN = Pattern.compile(".*(第\\s*\\d+\\s*(章|节|讲|页)|章节|教材|课件|课程资料|资料中|原文|公式|例题|图表|算法|步骤|机制|条件|过程|根据).*");
    private static final Pattern SUMMARY_PATTERN = Pattern.compile(".*(综述|概括|总结|整体|全局|主题|脉络|知识体系|框架|overview).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLORATION_PATTERN = Pattern.compile(".*(关联|联系|扩展|延伸|发散|迁移|类似|对比|比较|影响|应用|探索).*");
    private static final Pattern DEFINITION_PATTERN = Pattern.compile(".*(什么是|是什么|定义|概念|含义|解释一下|请解释|介绍一下|简述).*");
    private static final Pattern EVIDENCE_PATTERN = Pattern.compile(".*(证据|依据|来源|引用|出处|佐证|材料依据|课程证据|交叉验证|可靠).*");
    private static final Pattern RELATION_PATTERN = Pattern.compile(".*(关系|联系|关联|比较|对比|区别|异同|影响).*");
    private static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(".*(它|这个|这一个|该概念|上面|上述|前者|后者|刚才|继续).*");

    private static final Map<String, List<String>> ROUTE_REFERENCES = Map.of(
            "basic", List.of("什么是", "定义", "概念", "含义", "简述", "解释"),
            "local", List.of("第几章", "教材", "课件", "原文", "算法步骤", "机制条件", "例题"),
            "global", List.of("综述", "总结", "整体脉络", "知识体系", "课程框架"),
            "drift", List.of("关联", "扩展", "迁移", "应用", "比较", "探索"),
            "hybrid_v0", List.of("综合比较", "证据依据", "来源引用", "交叉验证", "关系并给出材料")
    );

    private final QaSessionsService qaSessionsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private CourseAccessService courseAccessService;

    @Autowired(required = false)
    public void setCourseAccessService(CourseAccessService courseAccessService) {
        this.courseAccessService = courseAccessService;
    }

    public QaModeRecommendationResponse recommend(QaModeRecommendationRequest request, AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }

        RoutingScope scope = resolveScope(request, currentUser);
        String question = trimToEmpty(request.getQuestion());
        boolean betaHybridEnabled = Boolean.TRUE.equals(request.getBetaHybridEnabled());
        boolean hasContext = Boolean.TRUE.equals(request.getHasConversationContext()) || scope.hasSession();

        Map<String, Double> scores = initialScores();
        Set<String> reasons = new LinkedHashSet<>();
        applyIntentScores(question, hasContext, scores, reasons);
        applyReferenceScores(question, scores);

        String bestMode = bestMode(scores, betaHybridEnabled);
        String rawBestMode = rawBestMode(scores);
        String fallbackMode = fallbackMode(scores, reasons);
        if ("hybrid_v0".equals(rawBestMode) && !betaHybridEnabled) {
            reasons.add("hybrid_beta_disabled");
            bestMode = fallbackMode;
        }
        if (reasons.isEmpty()) {
            reasons.add("default_basic");
        }

        return QaModeRecommendationResponse.of(
                bestMode,
                fallbackMode,
                confidence(scores, bestMode),
                new ArrayList<>(reasons),
                reasonText(bestMode, reasons, betaHybridEnabled),
                betaHybridEnabled,
                hasContext,
                STRATEGY,
                roundedScores(scores)
        );
    }

    private RoutingScope resolveScope(QaModeRecommendationRequest request, AuthenticatedUser currentUser) {
        if (request.getSessionId() != null) {
            QaSessions session = qaSessionsService.getRequiredById(request.getSessionId());
            if (!currentUser.id().equals(session.getUserId())) {
                throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "只能访问自己的问答会话");
            }
            if (session.getKnowledgeBaseId() != null) {
                KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
                validateKnowledgeBaseScope(session.getCourseId(), knowledgeBase, currentUser);
            } else if (StringUtils.hasText(session.getCourseId())) {
                validateCourseReadable(session.getCourseId(), currentUser);
            }
            return new RoutingScope(session.getCourseId(), session.getKnowledgeBaseId(), true);
        }

        if (request.getKnowledgeBaseId() != null) {
            KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
            validateKnowledgeBaseScope(request.getCourseId(), knowledgeBase, currentUser);
            return new RoutingScope(knowledgeBase.getCourseId(), knowledgeBase.getId(), false);
        }

        if (StringUtils.hasText(request.getCourseId())) {
            validateCourseReadable(request.getCourseId(), currentUser);
            return new RoutingScope(request.getCourseId(), null, false);
        }
        return new RoutingScope("", null, false);
    }

    private void validateKnowledgeBaseScope(String courseId, KnowledgeBases knowledgeBase, AuthenticatedUser currentUser) {
        if (StringUtils.hasText(courseId) && !Objects.equals(courseId, knowledgeBase.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "知识库不属于当前课程");
        }
        validateCourseReadable(knowledgeBase.getCourseId(), currentUser);
    }

    private void validateCourseReadable(String courseId, AuthenticatedUser currentUser) {
        if (courseAccessService != null && StringUtils.hasText(courseId)) {
            courseAccessService.assertCourseReadable(courseId, currentUser.userCode());
        }
    }

    private Map<String, Double> initialScores() {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("basic", 0.30D);
        scores.put("local", 0.18D);
        scores.put("global", 0.12D);
        scores.put("drift", 0.10D);
        scores.put("hybrid_v0", 0.05D);
        return scores;
    }

    private void applyIntentScores(String question, boolean hasContext, Map<String, Double> scores, Set<String> reasons) {
        if (matches(DEFINITION_PATTERN, question)) {
            add(scores, "basic", 0.55D);
            reasons.add("definition_intent");
        }
        if (matches(MATERIAL_PATTERN, question)) {
            add(scores, "local", 0.62D);
            reasons.add("material_locator");
        }
        if (matches(SUMMARY_PATTERN, question)) {
            add(scores, "global", 0.72D);
            reasons.add("summary_intent");
        }
        if (matches(EXPLORATION_PATTERN, question)) {
            add(scores, "drift", 0.64D);
            reasons.add("exploration_intent");
        }
        boolean evidence = matches(EVIDENCE_PATTERN, question);
        boolean relation = matches(RELATION_PATTERN, question);
        boolean followUp = hasContext && matches(FOLLOW_UP_PATTERN, question);
        if (evidence) {
            add(scores, "local", 0.20D);
            add(scores, "hybrid_v0", 0.30D);
            reasons.add("evidence_seeking");
        }
        if (followUp) {
            add(scores, "local", 0.16D);
            add(scores, "hybrid_v0", 0.20D);
            reasons.add("follow_up_context");
        }
        if (evidence && relation) {
            add(scores, "hybrid_v0", 0.45D);
            reasons.add("evidence_relation_intent");
        }
        if (question.length() <= 24 && reasons.contains("definition_intent") && reasons.size() == 1) {
            add(scores, "basic", 0.12D);
        }
        if (question.length() >= 42 && relation && evidence) {
            add(scores, "hybrid_v0", 0.18D);
        }
    }

    private void applyReferenceScores(String question, Map<String, Double> scores) {
        Set<String> questionTokens = tokens(question);
        for (Map.Entry<String, List<String>> entry : ROUTE_REFERENCES.entrySet()) {
            double best = 0D;
            for (String reference : entry.getValue()) {
                best = Math.max(best, overlap(questionTokens, tokens(reference)));
            }
            if (best > 0D) {
                add(scores, entry.getKey(), Math.min(0.18D, best * 0.18D));
            }
        }
    }

    private Set<String> tokens(String value) {
        String text = trimToEmpty(value).toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : text.split("[\\s,，。！？；;：:、（）()《》\"']+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        for (int i = 0; i + 2 <= text.length(); i++) {
            String token = text.substring(i, i + 2);
            if (token.codePoints().anyMatch(Character::isIdeographic)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double overlap(Set<String> questionTokens, Set<String> referenceTokens) {
        if (questionTokens.isEmpty() || referenceTokens.isEmpty()) {
            return 0D;
        }
        long matched = referenceTokens.stream().filter(questionTokens::contains).count();
        return matched / (double) referenceTokens.size();
    }

    private boolean matches(Pattern pattern, String question) {
        return pattern.matcher(question).matches();
    }

    private void add(Map<String, Double> scores, String mode, double value) {
        scores.compute(mode, (key, score) -> (score == null ? 0D : score) + value);
    }

    private String rawBestMode(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("basic");
    }

    private String bestMode(Map<String, Double> scores, boolean betaHybridEnabled) {
        String best = rawBestMode(scores);
        if ("hybrid_v0".equals(best) && !betaHybridEnabled) {
            return bestNonHybridMode(scores);
        }
        return best;
    }

    private String bestNonHybridMode(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .filter(entry -> !"hybrid_v0".equals(entry.getKey()))
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("basic");
    }

    private String fallbackMode(Map<String, Double> scores, Set<String> reasons) {
        if (reasons.contains("evidence_seeking") || reasons.contains("evidence_relation_intent")) {
            return "local";
        }
        return bestNonHybridMode(scores);
    }

    private double confidence(Map<String, Double> scores, String selectedMode) {
        double selected = scores.getOrDefault(selectedMode, 0D);
        double second = scores.entrySet().stream()
                .filter(entry -> !selectedMode.equals(entry.getKey()))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0D);
        double value = 0.55D + Math.min(0.39D, Math.max(0D, selected - second) * 0.35D);
        return round(value);
    }

    private Map<String, Double> roundedScores(Map<String, Double> scores) {
        Map<String, Double> rounded = new LinkedHashMap<>();
        for (String mode : MODES) {
            rounded.put(mode, round(scores.getOrDefault(mode, 0D)));
        }
        return rounded;
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private String reasonText(String mode, Set<String> reasons, boolean betaHybridEnabled) {
        if ("hybrid_v0".equals(mode)) {
            return "已开启 Beta，问题需要证据融合，推荐混合检索。";
        }
        if (reasons.contains("hybrid_beta_disabled")) {
            return "问题具备混合检索信号，但 Beta 未开启，已回退到 " + mode + " 模式。";
        }
        return switch (mode) {
            case "local" -> "问题需要定位课程材料或具体机制，推荐精确定位模式。";
            case "global" -> "问题在请求课程整体脉络，推荐全局综述模式。";
            case "drift" -> "问题偏向关联、扩展或迁移，推荐探索扩展模式。";
            default -> "问题更像事实或定义查询，推荐快速问答模式。";
        };
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record RoutingScope(String courseId, Long knowledgeBaseId, boolean hasSession) {
    }
}
