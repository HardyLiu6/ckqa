package org.ysu.ckqaback.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户头像读取内容。
 */
@Getter
@Builder
public class UserAvatarContent {

    private final byte[] bytes;
    private final String contentType;
    private final long fileSize;
}
