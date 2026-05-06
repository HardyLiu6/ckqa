package org.ysu.ckqaback.course;

/**
 * 从对象存储读取到的课程封面内容。
 */
public record StoredCourseCoverObject(byte[] bytes, String contentType, long size) {
}
