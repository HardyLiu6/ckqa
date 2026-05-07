package org.ysu.ckqaback.pdf.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 解析产物二进制内容。
 */
@Getter
@Builder
public class ParseResultContent {

    private final String fileName;
    private final String contentType;
    private final Long fileSize;
    private final byte[] bytes;
}
