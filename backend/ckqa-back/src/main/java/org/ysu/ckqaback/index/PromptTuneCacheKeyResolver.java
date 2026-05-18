package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.MaterialObjects;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.MaterialObjectsService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 计算自动调优的缓存键。
 * <p>
 * 缓存键 = sha256(materialId:fileMd5 排序后用换行拼接)。
 * 同一组资料即使顺序不同，缓存键也保持一致；任一资料重新解析（fileMd5 变化）会自动失效。
 */
@Service
@RequiredArgsConstructor
public class PromptTuneCacheKeyResolver {

    private final CourseMaterialsService courseMaterialsService;
    private final MaterialObjectsService materialObjectsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析 build run 的资料快照，构建缓存键和资料明细。
     */
    public PromptTuneCacheContext resolve(String selectedMaterialIdsJson, String courseId) {
        List<Long> materialIds = parseMaterialIds(selectedMaterialIdsJson);
        if (materialIds.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "请先在第 1 步选择课程资料"
            );
        }

        Map<Long, String> materialMd5 = new LinkedHashMap<>();
        for (Long materialId : materialIds) {
            CourseMaterials material = courseMaterialsService.getRequiredById(materialId);
            if (StringUtils.hasText(courseId) && !Objects.equals(material.getCourseId(), courseId)) {
                throw new BusinessException(
                        ApiResultCode.BAD_REQUEST,
                        HttpStatus.BAD_REQUEST,
                        "资料 " + materialId + " 不属于当前知识库课程"
                );
            }
            String md5 = resolveMd5(material);
            if (!StringUtils.hasText(md5)) {
                throw new BusinessException(
                        ApiResultCode.BAD_REQUEST,
                        HttpStatus.BAD_REQUEST,
                        "资料 " + materialId + " 缺少文件指纹，无法用于自动调优"
                );
            }
            materialMd5.put(materialId, md5);
        }

        List<String> sortedTokens = new ArrayList<>();
        for (Map.Entry<Long, String> entry : materialMd5.entrySet()) {
            sortedTokens.add(entry.getKey() + ":" + entry.getValue());
        }
        Collections.sort(sortedTokens);
        String cacheKey = sha256Hex(String.join("\n", sortedTokens));

        return new PromptTuneCacheContext(materialIds, materialMd5, cacheKey);
    }

    private List<Long> parseMaterialIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<List<Object>>() {
            });
            return raw.stream()
                    .map(this::toLongOrNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception exception) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "资料选择快照格式非法"
            );
        }
    }

    private Long toLongOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveMd5(CourseMaterials material) {
        Long objectId = material.getMaterialObjectId();
        if (objectId == null) {
            return null;
        }
        MaterialObjects object = materialObjectsService.getById(objectId);
        return object == null ? null : object.getFileMd5();
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 缓存解析结果。
     */
    public record PromptTuneCacheContext(
            List<Long> materialIds,
            Map<Long, String> materialMd5,
            String cacheKey
    ) {
    }
}
