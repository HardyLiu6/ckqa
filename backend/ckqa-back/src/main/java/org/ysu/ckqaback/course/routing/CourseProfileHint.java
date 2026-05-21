package org.ysu.ckqaback.course.routing;

import java.util.List;

/**
 * 从 GraphRAG 输入/输出中抽取的课程画像提示。
 */
public record CourseProfileHint(String heading, List<String> keywords) {
}
