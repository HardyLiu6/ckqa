package org.ysu.ckqaback.qa.context;

public interface TokenBudgetEstimator {

    BudgetSizeEstimate estimate(String text, Integer charsOverride);

    default BudgetSizeEstimate estimate(String text) {
        return estimate(text, null);
    }
}
