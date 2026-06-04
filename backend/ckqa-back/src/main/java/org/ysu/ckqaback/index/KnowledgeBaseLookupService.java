package org.ysu.ckqaback.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.cache.StudentCacheKeyFactory;
import org.ysu.ckqaback.cache.StudentRedisCacheService;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.KnowledgeBaseCreateRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseDetailResponse;
import org.ysu.ckqaback.index.dto.KnowledgeBaseQueryRequest;
import org.ysu.ckqaback.index.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.index.dto.KnowledgeBaseUpdateRequest;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter KB_CODE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int KB_CODE_MAX_LENGTH = 128;

    private final KnowledgeBasesService knowledgeBasesService;
    private final IndexRunsService indexRunsService;
    private final CoursesService coursesService;
    private final KnowledgeBaseBuildRunsService buildRunsService;
    private StudentRedisCacheService studentRedisCacheService;
    private StudentCacheKeyFactory studentCacheKeyFactory;

    @Autowired(required = false)
    public void setStudentRedisCacheService(StudentRedisCacheService studentRedisCacheService) {
        this.studentRedisCacheService = studentRedisCacheService;
    }

    @Autowired(required = false)
    public void setStudentCacheKeyFactory(StudentCacheKeyFactory studentCacheKeyFactory) {
        this.studentCacheKeyFactory = studentCacheKeyFactory;
    }

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
        String requestedKbCode = normalizeNullableText(request.getKbCode());
        Courses course = coursesService.getOne(new LambdaQueryWrapper<Courses>()
                .eq(Courses::getCourseId, courseId)
                .last("LIMIT 1"));
        if (course == null) {
            throw new BusinessException(ApiResultCode.COURSE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if ("archived".equalsIgnoreCase(course.getStatus())) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.CONFLICT,
                    "已归档课程不可编辑，请先撤销归档"
            );
        }
        LocalDateTime now = LocalDateTime.now();
        String kbCode = StringUtils.hasText(requestedKbCode)
                ? requestedKbCode
                : generateAvailableKnowledgeBaseCode(courseId, now);
        if (StringUtils.hasText(requestedKbCode) && knowledgeBaseCodeExists(courseId, kbCode)) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_CODE_EXISTS, HttpStatus.CONFLICT);
        }

        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setCourseId(courseId);
        knowledgeBase.setKbCode(kbCode);
        knowledgeBase.setName(normalizeText(request.getName()));
        knowledgeBase.setDescription(normalizeNullableText(request.getDescription()));
        knowledgeBase.setStatus(defaultIfBlank(request.getStatus(), "draft"));
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBasesService.save(knowledgeBase);
        evictStudentCourseCaches();

        return KnowledgeBaseDetailResponse.fromEntity(knowledgeBase, null, 0L, 0L);
    }

    public KnowledgeBaseDetailResponse updateKnowledgeBase(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(id);
        knowledgeBase.setName(normalizeText(request.getName()));
        knowledgeBase.setDescription(normalizeNullableText(request.getDescription()));
        knowledgeBase.setStatus(normalizeText(request.getStatus()));
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBasesService.updateById(knowledgeBase);
        evictStudentCourseCaches();
        return getKnowledgeBase(id);
    }

    public void deleteKnowledgeBase(Long id) {
        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(id);
        if (!buildRunsService.listByKnowledgeBaseId(id).isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.CONFLICT,
                    "知识库已有构建历史，请改为归档保留运行记录"
            );
        }
        knowledgeBasesService.removeById(id);
        evictStudentCourseCaches();
        evictHybridReadinessCaches(knowledgeBase.getId());
    }

    private void evictStudentCourseCaches() {
        if (studentRedisCacheService == null || studentCacheKeyFactory == null) {
            return;
        }
        studentRedisCacheService.evictByPattern(studentCacheKeyFactory.coursesPattern());
        studentRedisCacheService.evictByPattern(studentCacheKeyFactory.courseKnowledgeBasesPattern());
    }

    private void evictHybridReadinessCaches(Long knowledgeBaseId) {
        if (studentRedisCacheService == null || studentCacheKeyFactory == null) {
            return;
        }
        studentRedisCacheService.evictByPattern(studentCacheKeyFactory.hybridReadinessPattern(knowledgeBaseId));
    }

    private IndexRuns latestIndexRun(List<IndexRuns> indexRuns) {
        return indexRuns.stream()
                .max(Comparator
                        .comparing(IndexRuns::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(IndexRuns::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean matchesStatus(KnowledgeBases knowledgeBase, String status) {
        if (!StringUtils.hasText(status)) {
            return !"archived".equalsIgnoreCase(knowledgeBase.getStatus());
        }
        if ("all".equalsIgnoreCase(status)) {
            return true;
        }
        return Objects.equals(normalize(knowledgeBase.getStatus()), normalize(status));
    }

    private String generateAvailableKnowledgeBaseCode(String courseId, LocalDateTime now) {
        String baseCode = limitCodeLength("kb-" + normalize(courseId) + "-" + now.format(KB_CODE_TIMESTAMP_FORMATTER), 0);
        String candidate = baseCode;
        int sequence = 2;
        while (knowledgeBaseCodeExists(courseId, candidate)) {
            candidate = appendCodeSuffix(baseCode, sequence);
            sequence++;
        }
        return candidate;
    }

    private boolean knowledgeBaseCodeExists(String courseId, String kbCode) {
        return knowledgeBasesService.count(new LambdaQueryWrapper<KnowledgeBases>()
                .eq(KnowledgeBases::getCourseId, courseId)
                .eq(KnowledgeBases::getKbCode, kbCode)) > 0;
    }

    private String appendCodeSuffix(String baseCode, int sequence) {
        String suffix = "-" + sequence;
        return limitCodeLength(baseCode, suffix.length()) + suffix;
    }

    private String limitCodeLength(String value, int reservedSuffixLength) {
        int maxLength = Math.max(1, KB_CODE_MAX_LENGTH - reservedSuffixLength);
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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
