package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * GraphRAG 输入同步请求。
 */
@Getter
@Setter
public class BuildRunGraphInputRequest {

    private String jsonFile = "section_docs.json";

    private Boolean exportMissing = true;
}
