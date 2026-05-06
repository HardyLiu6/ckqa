package org.ysu.ckqaback.user;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.course.CourseCoverObjectStorage;
import org.ysu.ckqaback.course.StoredCourseCoverObject;
import org.ysu.ckqaback.entity.Users;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.UsersService;
import org.ysu.ckqaback.user.dto.UserAvatarContent;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 用户头像对象存储与默认头像兜底。
 */
@Service
@RequiredArgsConstructor
public class UserAvatarService {

    public static final String DEFAULT_USER_AVATAR_FILE_NAME = "default-user-avatar.svg";
    public static final String DEFAULT_USER_AVATAR_URL = "/api/v1/user-avatars/" + DEFAULT_USER_AVATAR_FILE_NAME;
    private static final String DEFAULT_USER_AVATAR_RESOURCE = "static/assets/user-avatars/default-user-avatar.svg";

    private static final Map<String, String> SUPPORTED_IMAGE_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp"
    );

    private final UserAvatarProperties properties;
    private final CourseCoverObjectStorage storage;
    private final UsersService usersService;

    public Users storeForUser(Users user, MultipartFile file) {
        if (user == null) {
            throw new BusinessException(ApiResultCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        validateFile(file);
        String contentType = normalizeContentType(file.getContentType());
        String extension = SUPPORTED_IMAGE_EXTENSIONS.get(contentType);
        String fileName = "user-avatar-" + user.getId() + "-" + UUID.randomUUID() + "." + extension;
        String objectKey = buildObjectKey(fileName);

        try (InputStream inputStream = file.getInputStream()) {
            storage.put(properties.getBucketName(), objectKey, inputStream, file.getSize(), contentType);
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "用户头像保存失败");
        }

        user.setAvatarBucket(properties.getBucketName());
        user.setAvatarObjectKey(objectKey);
        user.setAvatarContentType(contentType);
        user.setAvatarUpdatedAt(LocalDateTime.now());
        usersService.updateById(user);
        return user;
    }

    public UserAvatarContent load(String fileName) {
        String normalizedFileName = normalizeFileName(fileName);
        if (DEFAULT_USER_AVATAR_FILE_NAME.equals(normalizedFileName)) {
            return loadDefaultAvatar();
        }
        try {
            StoredCourseCoverObject object = storage.get(properties.getBucketName(), buildObjectKey(normalizedFileName));
            if (object == null) {
                throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "用户头像不存在");
            }
            return UserAvatarContent.builder()
                    .bytes(object.bytes())
                    .contentType(object.contentType())
                    .fileSize(object.size())
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "用户头像不存在");
        }
    }

    public static String resolveResponseAvatarUrl(Users user) {
        if (user == null || !StringUtils.hasText(user.getAvatarObjectKey())) {
            return DEFAULT_USER_AVATAR_URL;
        }
        String fileName = user.getAvatarObjectKey();
        int slashIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }
        if (!StringUtils.hasText(fileName)) {
            return DEFAULT_USER_AVATAR_URL;
        }
        return "/api/v1/user-avatars/" + fileName;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "用户头像文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "用户头像文件不能超过2MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!SUPPORTED_IMAGE_EXTENSIONS.containsKey(contentType)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "用户头像仅支持PNG、JPG或WEBP格式");
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

    private String normalizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("..")) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "用户头像文件名不合法");
        }
        return fileName.trim();
    }

    private UserAvatarContent loadDefaultAvatar() {
        ClassPathResource resource = new ClassPathResource(DEFAULT_USER_AVATAR_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return UserAvatarContent.builder()
                    .bytes(bytes)
                    .contentType("image/svg+xml")
                    .fileSize(bytes.length)
                    .build();
        } catch (IOException ex) {
            throw new BusinessException(ApiResultCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "默认用户头像读取失败");
        }
    }
}
