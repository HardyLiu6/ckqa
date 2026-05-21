package org.ysu.ckqaback.course.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.course.CourseMetadataJson;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseProfileHintsRequest;
import org.ysu.ckqaback.integration.graphrag.GraphRagCourseRoutingClient;
import org.ysu.ckqaback.service.IndexArtifactsService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 通过 GraphRAG Python 内部接口抽取课程画像 hints。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphRagCourseProfileHintProvider implements CourseProfileHintProvider {

    private final GraphRagCourseRoutingClient graphRagClient;
    private final IndexArtifactsService indexArtifactsService;
    private final CourseRoutingProperties properties;

    @Override
    public List<CourseProfileHint> loadHints(Courses course, List<KnowledgeBases> knowledgeBases) {
        if (!properties.isProfileHintsEnabled() || course == null || !StringUtils.hasText(course.getCourseId())) {
            return List.of();
        }
        List<String> dataDirUris = resolveDataDirUris(knowledgeBases);
        if (dataDirUris.isEmpty()) {
            return List.of();
        }
        try {
            var response = graphRagClient.extractProfileHints(new GraphRagCourseProfileHintsRequest(
                    course.getCourseId(),
                    dataDirUris,
                    properties.getProfileHintsMaxHints(),
                    seedKeywords(course, knowledgeBases)
            ));
            if (response == null || response.items() == null) {
                return List.of();
            }
            return response.items().stream()
                    .filter(item -> StringUtils.hasText(item.heading()))
                    .map(item -> new CourseProfileHint(item.heading(), item.keywords() == null ? List.of() : item.keywords()))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("课程画像 hints 抽取失败，courseId={}: {}", course.getCourseId(), ex.getMessage());
            return List.of();
        }
    }

    private List<String> resolveDataDirUris(List<KnowledgeBases> knowledgeBases) {
        LinkedHashSet<String> dataDirUris = new LinkedHashSet<>();
        for (KnowledgeBases knowledgeBase : knowledgeBases == null ? List.<KnowledgeBases>of() : knowledgeBases) {
            if (!"active".equalsIgnoreCase(knowledgeBase.getStatus()) || knowledgeBase.getActiveIndexRunId() == null) {
                continue;
            }
            indexArtifactsService.listByIndexRunId(knowledgeBase.getActiveIndexRunId()).stream()
                    .filter(artifact -> "output_dir".equals(artifact.getArtifactType()))
                    .filter(artifact -> "ready".equals(artifact.getArtifactStatus()))
                    .map(IndexArtifacts::getStorageUri)
                    .filter(this::isSafeRelativeUri)
                    .forEach(dataDirUris::add);
        }
        return new ArrayList<>(dataDirUris);
    }

    private boolean isSafeRelativeUri(String storageUri) {
        if (!StringUtils.hasText(storageUri) || storageUri.contains("\\") || storageUri.contains("/home/")) {
            return false;
        }
        java.nio.file.Path path = java.nio.file.Path.of(storageUri);
        if (path.isAbsolute()) {
            return false;
        }
        java.nio.file.Path normalized = path.normalize();
        return !normalized.startsWith("..");
    }

    private List<String> seedKeywords(Courses course, List<KnowledgeBases> knowledgeBases) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, course.getCourseName());
        addKeyword(keywords, course.getCategory());
        CourseMetadataJson.fromJsonOrEmpty(course.getTags()).forEach(value -> addKeyword(keywords, value));
        CourseMetadataJson.fromJsonOrEmpty(course.getObjectives()).forEach(value -> addKeyword(keywords, value));
        for (KnowledgeBases knowledgeBase : knowledgeBases == null ? List.<KnowledgeBases>of() : knowledgeBases) {
            addKeyword(keywords, knowledgeBase.getName());
            addKeyword(keywords, knowledgeBase.getDescription());
        }
        return new ArrayList<>(keywords);
    }

    private void addKeyword(LinkedHashSet<String> keywords, String value) {
        if (StringUtils.hasText(value)) {
            keywords.add(value.trim());
        }
    }
}
