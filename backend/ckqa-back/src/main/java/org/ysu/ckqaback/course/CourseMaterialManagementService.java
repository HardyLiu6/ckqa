package org.ysu.ckqaback.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.CourseMaterialQueryRequest;
import org.ysu.ckqaback.course.dto.CourseMaterialResponse;
import org.ysu.ckqaback.course.dto.CourseMaterialUpdateRequest;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.MaterialObjects;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.MaterialObjectsService;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课程资料上传、列表和元数据管理服务。
 */
@Service
@RequiredArgsConstructor
public class CourseMaterialManagementService {

    private static final List<String> ALLOWED_MATERIAL_TYPES = List.of(
            "textbook", "handout", "slides", "lab_guide", "exam", "reference", "other"
    );

    private final CourseMaterialsService courseMaterialsService;
    private final MaterialObjectsService materialObjectsService;
    private final CoursesService coursesService;
    private final CourseAccessService courseAccessService;
    private final CourseMaterialProperties properties;
    private final CourseCoverObjectStorage storage;

    public ApiPageData<CourseMaterialResponse> listMaterials(
            String courseId,
            CourseMaterialQueryRequest request,
            String actorUserCode
    ) {
        courseAccessService.assertCourseReadable(courseId, actorUserCode);
        List<CourseMaterials> sourceMaterials = courseMaterialsService.listByCourseId(courseId);
        Map<Long, MaterialObjects> objectsById = objectsById(sourceMaterials);
        List<CourseMaterials> materials = sourceMaterials.stream()
                .filter(material -> matchesKeyword(material, objectsById.get(material.getMaterialObjectId()), request.getKeyword()))
                .filter(material -> matches(material.getMaterialType(), request.getMaterialType()))
                .filter(material -> matches(material.getParseStatus(), request.getParseStatus()))
                .toList();
        List<CourseMaterialResponse> responses = materials.stream()
                .map(material -> CourseMaterialResponse.fromEntity(material, objectsById.get(material.getMaterialObjectId())))
                .toList();
        int current = request.getPage() == null ? 1 : request.getPage();
        int size = request.getSize() == null ? 20 : request.getSize();
        long total = responses.size();
        long pages = size <= 0 ? 0 : (long) Math.ceil(total / (double) size);
        int from = Math.min((current - 1) * size, responses.size());
        int to = Math.min(from + size, responses.size());
        return new ApiPageData<>(responses.subList(from, to), current, size, total, pages);
    }

    public CourseMaterialResponse getMaterial(String courseId, Long materialId, String actorUserCode) {
        courseAccessService.assertCourseReadable(courseId, actorUserCode);
        CourseMaterials material = getRequiredCourseMaterial(courseId, materialId);
        MaterialObjects object = materialObjectsService.getById(material.getMaterialObjectId());
        return CourseMaterialResponse.fromEntity(material, object);
    }

    @Transactional
    public CourseMaterialResponse uploadMaterial(
            String courseId,
            MultipartFile file,
            String displayName,
            String materialType,
            String actorUserCode
    ) {
        courseAccessService.assertCourseMembershipWritable(courseId, actorUserCode);
        assertCourseExists(courseId);
        validatePdf(file);
        String normalizedMaterialType = normalizeMaterialType(materialType);
        String normalizedDisplayName = normalizeDisplayName(displayName, file.getOriginalFilename());
        assertDisplayNameAvailable(courseId, normalizedDisplayName, null);

        byte[] bytes = readBytes(file);
        String md5 = md5Hex(bytes);
        MaterialObjects object = materialObjectsService.getByFileMd5(md5);
        if (object == null) {
            object = createMaterialObject(file, normalizedDisplayName, md5, bytes);
        }
        if (courseMaterialsService.count(new LambdaQueryWrapper<CourseMaterials>()
                .eq(CourseMaterials::getCourseId, courseId)
                .eq(CourseMaterials::getMaterialObjectId, object.getId())) > 0) {
            throw new BusinessException(ApiResultCode.COURSE_MATERIAL_EXISTS, HttpStatus.CONFLICT);
        }

        CourseMaterials material = new CourseMaterials();
        material.setCourseId(courseId);
        material.setMaterialObjectId(object.getId());
        material.setDisplayName(normalizedDisplayName);
        material.setMaterialType(normalizedMaterialType);
        material.setParseStatus("pending");
        material.setUploadTime(LocalDateTime.now());
        courseMaterialsService.save(material);
        return CourseMaterialResponse.fromEntity(material, object);
    }

    @Transactional
    public CourseMaterialResponse updateMaterial(
            String courseId,
            Long materialId,
            CourseMaterialUpdateRequest request,
            String actorUserCode
    ) {
        courseAccessService.assertCourseMembershipWritable(courseId, actorUserCode);
        CourseMaterials material = getRequiredCourseMaterial(courseId, materialId);
        String requestedDisplayName = trimToNull(request.getDisplayName());
        if (requestedDisplayName != null && !Objects.equals(requestedDisplayName, material.getDisplayName())) {
            assertDisplayNameAvailable(courseId, requestedDisplayName, materialId);
            material.setDisplayName(requestedDisplayName);
        }
        String requestedMaterialType = trimToNull(request.getMaterialType());
        if (requestedMaterialType != null) {
            material.setMaterialType(normalizeMaterialType(requestedMaterialType));
        }
        courseMaterialsService.updateById(material);
        return CourseMaterialResponse.fromEntity(material, materialObjectsService.getById(material.getMaterialObjectId()));
    }

    @Transactional
    public void deleteMaterial(String courseId, Long materialId, String actorUserCode) {
        courseAccessService.assertCourseMembershipWritable(courseId, actorUserCode);
        CourseMaterials material = getRequiredCourseMaterial(courseId, materialId);
        if ("processing".equalsIgnoreCase(material.getParseStatus())) {
            throw new BusinessException(ApiResultCode.PDF_PARSE_STATE_CONFLICT, HttpStatus.CONFLICT, "解析中的资料不能删除");
        }
        courseMaterialsService.removeById(materialId);
    }

    private MaterialObjects createMaterialObject(MultipartFile file, String displayName, String md5, byte[] bytes) {
        String objectKey = buildObjectKey(md5);
        try {
            storage.put(
                    properties.getBucketName(),
                    objectKey,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    "application/pdf"
            );
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "课程资料保存失败");
        }
        MaterialObjects object = new MaterialObjects();
        object.setOriginalFileName(normalizeOriginalFileName(file.getOriginalFilename(), displayName));
        object.setFileMd5(md5);
        object.setFileSize((long) bytes.length);
        object.setMimeType("application/pdf");
        object.setMinioBucket(properties.getBucketName());
        object.setMinioObjectKey(objectKey);
        materialObjectsService.save(object);
        return object;
    }

    private Map<Long, MaterialObjects> objectsById(List<CourseMaterials> materials) {
        List<Long> ids = materials.stream()
                .map(CourseMaterials::getMaterialObjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return materialObjectsService.listByIds(ids).stream()
                .collect(Collectors.toMap(MaterialObjects::getId, Function.identity(), (left, right) -> left));
    }

    private CourseMaterials getRequiredCourseMaterial(String courseId, Long materialId) {
        CourseMaterials material = courseMaterialsService.getRequiredById(materialId);
        if (!Objects.equals(courseId, material.getCourseId())) {
            throw new BusinessException(ApiResultCode.PDF_FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return material;
    }

    private void assertCourseExists(String courseId) {
        if (coursesService.count(new LambdaQueryWrapper<Courses>().eq(Courses::getCourseId, courseId)) == 0) {
            throw new BusinessException(ApiResultCode.COURSE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

    private void assertDisplayNameAvailable(String courseId, String displayName, Long excludedMaterialId) {
        LambdaQueryWrapper<CourseMaterials> wrapper = new LambdaQueryWrapper<CourseMaterials>()
                .eq(CourseMaterials::getCourseId, courseId)
                .eq(CourseMaterials::getDisplayName, displayName);
        if (excludedMaterialId != null) {
            wrapper.ne(CourseMaterials::getId, excludedMaterialId);
        }
        if (courseMaterialsService.count(wrapper) > 0) {
            throw new BusinessException(ApiResultCode.COURSE_MATERIAL_DISPLAY_NAME_EXISTS, HttpStatus.CONFLICT);
        }
    }

    private boolean matchesKeyword(CourseMaterials material, MaterialObjects object, String keyword) {
        String normalized = trimToNull(keyword);
        if (normalized == null) {
            return true;
        }
        return contains(material.getDisplayName(), normalized)
                || contains(material.getMaterialType(), normalized)
                || contains(material.getParseStatus(), normalized)
                || contains(object == null ? null : object.getOriginalFileName(), normalized)
                || contains(object == null ? null : object.getFileMd5(), normalized)
                || contains(object == null ? null : object.getMimeType(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private boolean matches(String actual, String expected) {
        String normalized = trimToNull(expected);
        return normalized == null || normalized.equalsIgnoreCase(actual);
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程资料文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程资料文件不能超过50MB");
        }
        String contentType = trimToNull(file.getContentType());
        String fileName = trimToNull(file.getOriginalFilename());
        boolean looksLikePdf = "application/pdf".equalsIgnoreCase(contentType)
                || (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        if (!looksLikePdf) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程资料仅支持PDF格式");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程资料读取失败");
        }
    }

    private String md5Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "课程资料指纹计算失败");
        }
    }

    private String buildObjectKey(String md5) {
        String prefix = properties.normalizeObjectPrefix();
        String fileName = md5 + ".pdf";
        return StringUtils.hasText(prefix) ? prefix + "/" + fileName : fileName;
    }

    private String normalizeMaterialType(String materialType) {
        String normalized = trimToNull(materialType);
        if (normalized == null) {
            return "textbook";
        }
        if (!ALLOWED_MATERIAL_TYPES.contains(normalized)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "资料类型不合法");
        }
        return normalized;
    }

    private String normalizeDisplayName(String displayName, String originalFileName) {
        String normalized = trimToNull(displayName);
        if (normalized == null) {
            normalized = normalizeOriginalFileName(originalFileName, "未命名资料.pdf");
        }
        if (normalized.length() > 255) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程资料名称不能超过255个字符");
        }
        return normalized;
    }

    private String normalizeOriginalFileName(String originalFileName, String fallback) {
        String normalized = trimToNull(originalFileName);
        if (normalized == null) {
            return fallback;
        }
        return normalized.replace("\\", "/")
                .substring(normalized.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
