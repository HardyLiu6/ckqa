package org.ysu.ckqaback.course;

import java.io.InputStream;

/**
 * 课程封面对对象存储的最小依赖边界。
 */
public interface CourseCoverObjectStorage {

    void put(String bucket, String objectKey, InputStream data, long size, String contentType) throws Exception;

    StoredCourseCoverObject get(String bucket, String objectKey) throws Exception;
}
