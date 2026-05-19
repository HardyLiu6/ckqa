package org.ysu.ckqaback.qa.ops;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 问答运维来源查询行。
 */
@Getter
@Setter
public class QaOperationSourceRow {
    private Long id;
    private Long retrievalLogId;
    private Integer rankPosition;
    private String documentKey;
    private String chunkId;
    private String sourceType;
    private String sourceRef;
    private String sourceFile;
    private String headingPath;
    private Integer pageStart;
    private Integer pageEnd;
    private String snippet;
    private BigDecimal score;
}
