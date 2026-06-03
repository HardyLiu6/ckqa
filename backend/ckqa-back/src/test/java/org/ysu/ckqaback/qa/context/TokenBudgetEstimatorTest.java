package org.ysu.ckqaback.qa.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBudgetEstimatorTest {

    @Test
    void shouldCountChineseAndEnglishTokensWithJtokkit() {
        TokenBudgetEstimator estimator = new JtokkitTokenBudgetEstimator();

        BudgetSizeEstimate estimate = estimator.estimate("死锁 deadlock scheduling", null);

        assertThat(estimate.chars()).isEqualTo("死锁 deadlock scheduling".length());
        assertThat(estimate.tokens()).isNotNull().isPositive();
        assertThat(estimate.tokenizer()).isEqualTo("jtokkit:o200k_base");
        assertThat(estimate.fallbackReason()).isNull();
    }

    @Test
    void shouldReturnCharOnlyEstimateForFallbackImplementation() {
        TokenBudgetEstimator estimator = new CharFallbackTokenBudgetEstimator();

        BudgetSizeEstimate estimate = estimator.estimate("只统计字符", 12);

        assertThat(estimate.chars()).isEqualTo(12);
        assertThat(estimate.tokens()).isNull();
        assertThat(estimate.tokenizer()).isEqualTo("char_fallback");
        assertThat(estimate.fallbackReason()).isEqualTo("tokenizer_unavailable");
    }

    @Test
    void shouldHandleEmptyText() {
        TokenBudgetEstimator estimator = new JtokkitTokenBudgetEstimator();

        BudgetSizeEstimate estimate = estimator.estimate("", null);

        assertThat(estimate.chars()).isZero();
        assertThat(estimate.tokens()).isZero();
        assertThat(estimate.tokenizer()).isEqualTo("jtokkit:o200k_base");
    }

    @Test
    void shouldCountSpecialTokenTextOrdinarilyWithoutThrowing() {
        TokenBudgetEstimator estimator = new JtokkitTokenBudgetEstimator();

        BudgetSizeEstimate estimate = estimator.estimate("<|endoftext|> 是普通课程文本", null);

        assertThat(estimate.tokens()).isNotNull().isPositive();
        assertThat(estimate.fallbackReason()).isNull();
    }
}
