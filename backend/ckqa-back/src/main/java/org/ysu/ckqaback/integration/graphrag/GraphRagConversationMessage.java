package org.ysu.ckqaback.integration.graphrag;

/**
 * 发送给 GraphRAG Python 的历史对话条目。
 */
public record GraphRagConversationMessage(String role, String content) {
}
