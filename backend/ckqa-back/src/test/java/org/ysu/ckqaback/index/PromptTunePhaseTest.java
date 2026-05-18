package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTunePhaseTest {

    @Test
    void match_chunkingDocuments() {
        assertThat(PromptTunePhase.match("Chunking documents..."))
                .contains(PromptTunePhase.CHUNKING);
    }

    @Test
    void match_retrievingLanguageModel_alsoChunking() {
        // 准备阶段的两个 INFO 行归并到 CHUNKING，避免给用户看内部细节。
        assertThat(PromptTunePhase.match("Retrieving language model configuration..."))
                .contains(PromptTunePhase.CHUNKING);
        assertThat(PromptTunePhase.match("Creating language model..."))
                .contains(PromptTunePhase.CHUNKING);
    }

    @Test
    void match_generatingDomain() {
        assertThat(PromptTunePhase.match("Generating domain..."))
                .contains(PromptTunePhase.DOMAIN);
    }

    @Test
    void match_detectingLanguage() {
        assertThat(PromptTunePhase.match("Detecting language..."))
                .contains(PromptTunePhase.LANGUAGE);
    }

    @Test
    void match_generatingPersona() {
        assertThat(PromptTunePhase.match("Generating persona..."))
                .contains(PromptTunePhase.PERSONA);
    }

    @Test
    void match_communityRanking() {
        assertThat(PromptTunePhase.match("Generating community report ranking description..."))
                .contains(PromptTunePhase.COMMUNITY_RANKING);
    }

    @Test
    void match_entityTypes() {
        // 不能被 ENTITY_EXTRACTION 等包含 "entity" 的更宽松规则错误抢走。
        assertThat(PromptTunePhase.match("Generating entity types..."))
                .contains(PromptTunePhase.ENTITY_TYPES);
    }

    @Test
    void match_examples() {
        assertThat(PromptTunePhase.match("Generating entity relationship examples..."))
                .contains(PromptTunePhase.EXAMPLES);
    }

    @Test
    void match_extractPrompt() {
        assertThat(PromptTunePhase.match("Generating entity extraction prompt..."))
                .contains(PromptTunePhase.EXTRACT_PROMPT);
    }

    @Test
    void match_summaryPrompt() {
        assertThat(PromptTunePhase.match("Generating entity summarization prompt..."))
                .contains(PromptTunePhase.SUMMARY_PROMPT);
    }

    @Test
    void match_communityRole() {
        assertThat(PromptTunePhase.match("Generating community reporter role..."))
                .contains(PromptTunePhase.COMMUNITY_ROLE);
    }

    @Test
    void match_communitySummary() {
        assertThat(PromptTunePhase.match("Generating community summarization prompt..."))
                .contains(PromptTunePhase.COMMUNITY_SUMMARY);
    }

    @Test
    void match_writing() {
        assertThat(PromptTunePhase.match("Writing prompts to /some/path"))
                .contains(PromptTunePhase.WRITING);
        assertThat(PromptTunePhase.match("Prompts written to /some/path"))
                .contains(PromptTunePhase.WRITING);
    }

    @Test
    void match_unknownMessageReturnsEmpty() {
        assertThat(PromptTunePhase.match("Some random log line we don't recognize"))
                .isEmpty();
    }

    @Test
    void match_blankReturnsEmpty() {
        assertThat(PromptTunePhase.match(null)).isEmpty();
        assertThat(PromptTunePhase.match("")).isEmpty();
        assertThat(PromptTunePhase.match("   ")).isEmpty();
    }

    @Test
    void progressPercentages_areMonotonic() {
        // 进度百分比必须严格递增，否则 PromptTuneLogTailer 的"不允许回退"语义就失效。
        PromptTunePhase[] phases = PromptTunePhase.values();
        for (int i = 1; i < phases.length; i++) {
            assertThat(phases[i].getProgressPercentage())
                    .as("phase %s 的进度应该严格大于前一个 phase %s", phases[i], phases[i - 1])
                    .isGreaterThan(phases[i - 1].getProgressPercentage());
        }
    }

    @Test
    void stageKey_prefixedWithPromptTune() {
        assertThat(PromptTunePhase.EXAMPLES.getStageKey()).isEqualTo("prompt_tune_examples");
        assertThat(PromptTunePhase.WRITING.getStageKey()).isEqualTo("prompt_tune_writing");
    }
}
