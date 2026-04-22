package org.ysu.ckqaback.pdf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GraphRAG 导出请求体。
 */
@Getter
@Setter
@NoArgsConstructor
public class ExportGraphRagRequest {

    @NotBlank(message = "mode不能为空")
    private String mode = "section";

    private boolean withPageDocs;

    private boolean force;
}
