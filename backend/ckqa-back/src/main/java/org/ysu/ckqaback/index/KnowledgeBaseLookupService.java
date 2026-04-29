package org.ysu.ckqaback.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.KnowledgeBaseCreateRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseDetailResponse;
import org.ysu.ckqaback.index.dto.KnowledgeBaseQueryRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 知识库读取聚合服务。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseLookupService {

    private final KnowledgeBasesService knowledgeBasesService;
    private final IndexRunsService indexRunsService;
    private final CoursesService coursesService;

    public ApiPageData<KnowledgeBaseSummaryResponse> listKnowledgeBases(KnowledgeBaseQueryRequest request) {
        long page = request.getPage() == null ? 1L : request.getPage();
        long size = request.getSize() == null ? 20L : request.getSize();

        List<KnowledgeBases> filtered = knowledgeBasesService.list().stream()
                .filter(knowledgeBase -> matchesStatus(knowledgeBase, request.getStatus()))
                .filter(knowledgeBase -> matchesKeyword(knowledgeBase, request.getKeyword()))
                .sorted(Comparator
                        .comparing(KnowledgeBases::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(KnowledgeBases::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long total = filtered.size();
        long offset = (page - 1L) * size;
        long pages = total == 0 ? 0 : (long) Math.ceil((double) total / size);
        if (offset >= total) {
            return new ApiPageData<>(List.of(), page, size, total, pages);
        }

        int fromIndex = (int) offset;
        int toIndex = (int) Math.min(offset + size, total);
        List<KnowledgeBaseSummaryResponse> items = filtered.subList(fromIndex, toIndex).stream()
                .map(knowledgeBase -> KnowledgeBaseSummaryResponse.fromEntity(
                        knowledgeBase,
                        latestIndexRun(indexRunsService.listByKnowledgeBaseId(knowledgeBase.getId()))
                ))
                .toList();
        return new ApiPageData<>(items, page, size, total, pages);
    }

    public KnowledgeBaseDetailResponse getKnowledgeBase(Long id) {
        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(id);
        List<IndexRuns> indexRuns = indexRunsService.listByKnowledgeBaseId(id);
        IndexRuns latestIndexRun = latestIndexRun(indexRuns);
        long successCount = indexRuns.stream()
                .filter(indexRun -> "success".equalsIgnoreCase(indexRun.getStatus()))
                .count();

        return KnowledgeBaseDetailResponse.fromEntity(
                knowledgeBase,
                latestIndexRun,
                (long) indexRuns.size(),
                successCount
        );
    }

    public KnowledgeBaseDetailResponse createKnowledgeBase(KnowledgeBaseCreateRequest request) {
        String courseId = normalizeText(request.getCourseId());
        String kbCode = normalizeText(request.getKbCode());
        if (coursesService.count(new LambdaQueryWrapper<Courses>().eq(Courses::getCourseId, courseId)) == 0) {
            throw new BusinessException(ApiResultCode.COURSE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (knowledgeBasesService.count(new LambdaQueryWrapper<KnowledgeBases>()
                .eq(KnowledgeBases::getCourseId, courseId)
                .eq(KnowledgeBases::getKbCode, kbCode)) > 0) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_CODE_EXISTS, HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setCourseId(courseId);
        knowledgeBase.setKbCode(kbCode);
        knowledgeBase.setName(normalizeText(request.getName()));
        knowledgeBase.setDescription(normalizeNullableText(request.getDescription()));
        knowledgeBase.setStatus(defaultIfBlank(request.getStatus(), "draft"));
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBasesService.save(knowledgeBase);

        return KnowledgeBaseDetailResponse.fromEntity(knowledgeBase, null, 0L, 0L);
    }

    private IndexRuns latestIndexRun(List<IndexRuns> indexRuns) {
        return indexRuns.stream()
                .max(Comparator
                        .comparing(IndexRuns::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
                        .thenComparing(IndexRuns::getId, Comparator.nullsFirst(Long::compareTo)))
                .orElse(null);
    }

    private boolean matchesStatus(KnowledgeBases knowledgeBase, String status) {
        return !StringUtils.hasText(status)
                || Objects.equals(normalize(knowledgeBase.getStatus()), normalize(status));
    }

    private boolean matchesKeyword(KnowledgeBases knowledgeBase, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = normalize(keyword);
        return contains(knowledgeBase.getCourseId(), normalizedKeyword)
                || contains(knowledgeBase.getKbCode(), normalizedKeyword)
                || contains(knowledgeBase.getName(), normalizedKeyword)
                || contains(knowledgeBase.getDescription(), normalizedKeyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && normalize(value).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? normalizeText(value) : fallback;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
