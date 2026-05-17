package org.ysu.ckqaback.qa.summary;

public interface QaSummaryClientPort {

    QaSummaryResult summarize(String previousSummary, String conversationText);
}
