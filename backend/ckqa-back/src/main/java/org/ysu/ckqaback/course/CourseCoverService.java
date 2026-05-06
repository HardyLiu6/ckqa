package org.ysu.ckqaback.course;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.dto.CourseCoverContent;
import org.ysu.ckqaback.course.dto.CourseCoverUploadResponse;
import org.ysu.ckqaback.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 课程封面文件存储与 URL 归一化。
 */
@Service
@RequiredArgsConstructor
public class CourseCoverService {

    public static final String DEFAULT_COURSE_COVER_FILE_NAME = "default-course-cover.svg";
    public static final String DEFAULT_COURSE_COVER_URL = "/api/v1/course-covers/" + DEFAULT_COURSE_COVER_FILE_NAME;
    private static final String DEFAULT_COURSE_COVER_RESOURCE = "static/assets/course-covers/default-course-cover.svg";

    private static final Map<String, String> SUPPORTED_IMAGE_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp"
    );

    private final CourseCoverProperties properties;
    private final CourseCoverObjectStorage storage;

    public CourseCoverUploadResponse store(MultipartFile file) {
        validateFile(file);
        String contentType = normalizeContentType(file.getContentType());
        String extension = SUPPORTED_IMAGE_EXTENSIONS.get(contentType);
        String fileName = "course-cover-" + UUID.randomUUID() + "." + extension;
        String objectKey = buildObjectKey(fileName);

        try (InputStream inputStream = file.getInputStream()) {
            storage.put(properties.getBucketName(), objectKey, inputStream, file.getSize(), contentType);
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "课程封面保存失败");
        }

        return CourseCoverUploadResponse.builder()
                .coverUrl(buildPublicUrl(fileName))
                .fileName(fileName)
                .contentType(contentType)
                .fileSize(file.getSize())
                .build();
    }

    public CourseCoverContent load(String fileName) {
        String normalizedFileName = normalizeFileName(fileName);
        if (DEFAULT_COURSE_COVER_FILE_NAME.equals(normalizedFileName)) {
            return loadDefaultCover();
        }
        try {
            StoredCourseCoverObject object = storage.get(properties.getBucketName(), buildObjectKey(normalizedFileName));
            if (object == null) {
                throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "课程封面不存在");
            }
            return CourseCoverContent.builder()
                    .bytes(object.bytes())
                    .contentType(object.contentType())
                    .fileSize(object.size())
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "课程封面不存在");
        }
    }

    public static String normalizeCoverUrl(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) {
            return DEFAULT_COURSE_COVER_URL;
        }
        String normalized = coverUrl.trim();
        if (isAllowedCoverUrl(normalized)) {
            return normalized;
        }
        throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程封面地址不合法");
    }

    public static String resolveResponseCoverUrl(String coverUrl) {
        return StringUtils.hasText(coverUrl) ? coverUrl.trim() : DEFAULT_COURSE_COVER_URL;
    }

    private static boolean isAllowedCoverUrl(String coverUrl) {
        return !coverUrl.contains("..")
                && !coverUrl.contains("\\")
                && !coverUrl.contains("://")
                && (DEFAULT_COURSE_COVER_URL.equals(coverUrl)
                || coverUrl.startsWith("/api/v1/course-covers/"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程封面文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程封面文件不能超过2MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!SUPPORTED_IMAGE_EXTENSIONS.containsKey(contentType)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程封面仅支持PNG、JPG或WEBP格式");
        }
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType)
                ? contentType.trim().toLowerCase(Locale.ROOT)
                : "";
    }

    private String buildObjectKey(String fileName) {
        String prefix = properties.normalizeObjectPrefix();
        return StringUtils.hasText(prefix) ? prefix + "/" + fileName : fileName;
    }

    private String buildPublicUrl(String fileName) {
        return properties.getPublicPathPrefix().replaceAll("/+$", "") + "/" + fileName;
    }

    private String normalizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("..")) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "课程封面文件名不合法");
        }
        return fileName.trim();
    }

    private CourseCoverContent loadDefaultCover() {
        ClassPathResource resource = new ClassPathResource(DEFAULT_COURSE_COVER_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return CourseCoverContent.builder()
                    .bytes(bytes)
                    .contentType("image/svg+xml")
                    .fileSize((long) bytes.length)
                    .build();
        } catch (IOException ex) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "默认课程封面读取失败");
        }
    }
}
