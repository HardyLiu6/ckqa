package org.ysu.ckqaback.course;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 MinIO 的课程封面对象存储实现。
 */
@Component
@RequiredArgsConstructor
public class MinioCourseCoverObjectStorage implements CourseCoverObjectStorage {

    private final CourseCoverProperties properties;
    private final Set<String> checkedBuckets = ConcurrentHashMap.newKeySet();
    private volatile MinioClient client;

    @Override
    public void put(String bucket, String objectKey, InputStream data, long size, String contentType) throws Exception {
        ensureBucket(bucket);
        getClient().putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(data, size, -1)
                .contentType(contentType)
                .build());
    }

    @Override
    public StoredCourseCoverObject get(String bucket, String objectKey) throws Exception {
        StatObjectResponse stat = getClient().statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
        try (InputStream inputStream = getClient().getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return new StoredCourseCoverObject(
                    inputStream.readAllBytes(),
                    stat.contentType(),
                    stat.size()
            );
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (checkedBuckets.contains(bucket)) {
            return;
        }
        synchronized (checkedBuckets) {
            if (!checkedBuckets.contains(bucket)) {
                boolean exists = getClient().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    getClient().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                checkedBuckets.add(bucket);
            }
        }
    }

    private MinioClient getClient() {
        MinioClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = MinioClient.builder()
                        .endpoint(resolveEndpoint())
                        .credentials(properties.getAccessKey(), properties.getSecretKey())
                        .build();
            }
            return client;
        }
    }

    private String resolveEndpoint() {
        String endpoint = properties.getEndpoint();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return (properties.isSecure() ? "https://" : "http://") + endpoint;
    }
}
