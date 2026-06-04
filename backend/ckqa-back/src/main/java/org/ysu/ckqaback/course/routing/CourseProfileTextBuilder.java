package org.ysu.ckqaback.course.routing;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.course.CourseMetadataJson;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.KbDocuments;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.KbDocumentsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 构造稳定课程画像文本。
 */
@Service
@RequiredArgsConstructor
public class CourseProfileTextBuilder {

    private static final int HINT_HEADING_LIMIT = 24;
    private static final int HINT_KEYWORD_LIMIT = 128;
    private static final int HINT_KEYWORDS_PER_SOURCE_LIMIT = 12;

    private final KnowledgeBasesService knowledgeBasesService;
    private final CourseMaterialsService courseMaterialsService;
    private final KbDocumentsService kbDocumentsService;
    private final CourseProfileHintProvider hintProvider;

    public CourseProfileSnapshot build(Courses course) {
        List<String> lines = new ArrayList<>();
        append(lines, "课程ID", course.getCourseId());
        append(lines, "课程名称", course.getCourseName());
        append(lines, "简介", course.getDescription());
        append(lines, "分类", course.getCategory());
        append(lines, "难度", course.getDifficulty());
        appendList(lines, "标签", CourseMetadataJson.fromJsonOrEmpty(course.getTags()));
        appendList(lines, "学习目标", CourseMetadataJson.fromJsonOrEmpty(course.getObjectives()));
        appendList(lines, "适合人群", CourseMetadataJson.fromJsonOrEmpty(course.getAudience()));

        List<KnowledgeBases> knowledgeBases = knowledgeBasesService.listByCourseId(course.getCourseId());
        List<String> kbNames = knowledgeBases.stream()
                .sorted(Comparator.comparing(KnowledgeBases::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .flatMap(kb -> Stream.of(kb.getName(), kb.getDescription()))
                .filter(StringUtils::hasText)
                .toList();
        appendList(lines, "知识库", kbNames);
        appendList(lines, "章节标题", collectKbDocumentTitles(knowledgeBases));
        List<CourseProfileHint> hints = hintProvider.loadHints(course, knowledgeBases);
        appendList(lines, "课程画像章节来源", hints.stream()
                .map(CourseProfileHint::heading)
                .filter(StringUtils::hasText)
                .limit(HINT_HEADING_LIMIT)
                .toList());
        appendList(lines, "课程画像关键词", selectHintKeywords(hints));

        List<String> materialNames = courseMaterialsService.listByCourseId(course.getCourseId()).stream()
                .sorted(Comparator.comparing(CourseMaterials::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CourseMaterials::getDisplayName)
                .filter(StringUtils::hasText)
                .toList();
        appendList(lines, "课程资料", materialNames);

        String profileText = String.join("\n", lines);
        return new CourseProfileSnapshot(profileText, sha256(profileText));
    }

    private List<String> collectKbDocumentTitles(List<KnowledgeBases> knowledgeBases) {
        return knowledgeBases.stream()
                .map(KnowledgeBases::getId)
                .filter(Objects::nonNull)
                .flatMap(knowledgeBaseId -> kbDocumentsService.list(new LambdaQueryWrapper<KbDocuments>()
                        .eq(KbDocuments::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByAsc(KbDocuments::getId)
                ).stream())
                .map(KbDocuments::getTitle)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(48)
                .toList();
    }

    private List<String> selectHintKeywords(List<CourseProfileHint> hints) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        List<List<String>> keywordBuckets = new ArrayList<>();
        for (CourseProfileHint hint : hints == null ? List.<CourseProfileHint>of() : hints) {
            if (hint == null || hint.keywords() == null) {
                continue;
            }
            List<String> bucket = hint.keywords().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .limit(HINT_KEYWORDS_PER_SOURCE_LIMIT)
                    .toList();
            if (!bucket.isEmpty()) {
                keywordBuckets.add(bucket);
            }
        }
        int maxBucketSize = keywordBuckets.stream().mapToInt(List::size).max().orElse(0);
        for (int keywordIndex = 0; keywordIndex < maxBucketSize; keywordIndex++) {
            for (List<String> bucket : keywordBuckets) {
                if (keywordIndex < bucket.size()) {
                    selected.add(bucket.get(keywordIndex));
                    if (selected.size() >= HINT_KEYWORD_LIMIT) {
                        return selected.stream().limit(HINT_KEYWORD_LIMIT).toList();
                    }
                }
            }
        }
        return selected.stream().limit(HINT_KEYWORD_LIMIT).toList();
    }

    private void append(List<String> lines, String label, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(label + "：" + value.trim());
        }
    }

    private void appendList(List<String> lines, String label, List<String> values) {
        List<String> normalized = values == null ? List.of() : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (!normalized.isEmpty()) {
            lines.add(label + "：" + String.join("、", normalized));
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("课程画像 hash 生成失败", ex);
        }
    }
}
