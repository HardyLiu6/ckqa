package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.index.dto.PromptDraftResponse;
import org.ysu.ckqaback.service.PromptDraftsService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptDraftListServiceTest {

    private PromptDraftsService promptDraftsService;
    private PromptDraftListService service;

    @BeforeEach
    void setUp() {
        promptDraftsService = mock(PromptDraftsService.class);
        service = new PromptDraftListService(promptDraftsService);
    }

    @Test
    void listProjectsAllFieldsToResponse() {
        PromptDrafts draft = new PromptDrafts();
        draft.setId(1L);
        draft.setKnowledgeBaseId(7L);
        draft.setName("课程 · 默认基线 · 2026-05-17");
        draft.setDescription("评分 0.7");
        draft.setSeed("system_default");
        draft.setCandidateId("default");
        draft.setPromptsJson("{\"extract_graph\":\"-Goal-\\n...\"}");
        draft.setSourceBuildRunId(18L);
        draft.setCompositeScore(new BigDecimal("0.7000"));
        draft.setCreatedAt(LocalDateTime.of(2026, 5, 17, 10, 0));
        draft.setUpdatedAt(LocalDateTime.of(2026, 5, 17, 10, 0));
        when(promptDraftsService.listByKnowledgeBaseId(7L)).thenReturn(List.of(draft));

        List<PromptDraftResponse> result = service.list(7L);
        assertThat(result).hasSize(1);
        PromptDraftResponse first = result.get(0);
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(first.getKnowledgeBaseId()).isEqualTo(7L);
        assertThat(first.getName()).isEqualTo("课程 · 默认基线 · 2026-05-17");
        assertThat(first.getSeed()).isEqualTo("system_default");
        assertThat(first.getCandidateId()).isEqualTo("default");
        // 注意：PromptDraftResponse 已在 Task 1 Step 6 删除 promptsJson 字段，
        // 这里不再断言 prompts 正文。
        assertThat(first.getSourceBuildRunId()).isEqualTo(18L);
        assertThat(first.getCompositeScore()).isEqualByComparingTo(new BigDecimal("0.7"));
    }

    @Test
    void listReturnsEmptyWhenNoDrafts() {
        when(promptDraftsService.listByKnowledgeBaseId(99L)).thenReturn(List.of());
        assertThat(service.list(99L)).isEmpty();
    }
}
