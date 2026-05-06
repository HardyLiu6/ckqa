package org.ysu.ckqaback.course;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.ysu.ckqaback.course.dto.CourseCoverContent;
import org.ysu.ckqaback.course.dto.CourseCoverUploadResponse;
import org.ysu.ckqaback.exception.BusinessException;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseCoverServiceTest {

    @Test
    void shouldStoreCourseCoverInMinioBucketAndLoadItBack() {
        CourseCoverProperties properties = new CourseCoverProperties();
        FakeCourseCoverObjectStorage storage = new FakeCourseCoverObjectStorage();
        CourseCoverService service = new CourseCoverService(properties, storage);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        CourseCoverUploadResponse response = service.store(file);

        assertThat(response.getCoverUrl()).startsWith("/api/v1/course-covers/course-cover-");
        assertThat(response.getCoverUrl()).endsWith(".png");
        assertThat(storage.savedBucket).isEqualTo("course-artifacts");
        assertThat(storage.savedObjectKey).startsWith("course-covers/course-cover-");
        assertThat(response.getFileSize()).isEqualTo(3L);

        CourseCoverContent content = service.load(response.getFileName());

        assertThat(content.getBytes()).containsExactly(1, 2, 3);
        assertThat(content.getContentType()).isEqualTo("image/png");
    }

    @Test
    void shouldNormalizeBlankCoverUrlToDefaultCover() {
        assertThat(CourseCoverService.normalizeCoverUrl(" "))
                .isEqualTo(CourseCoverService.DEFAULT_COURSE_COVER_URL);
    }

    @Test
    void shouldRejectExternalCoverUrl() {
        assertThatThrownBy(() -> CourseCoverService.normalizeCoverUrl("https://example.com/cover.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("课程封面地址不合法");
    }

    private static class FakeCourseCoverObjectStorage implements CourseCoverObjectStorage {

        private final Map<String, StoredCourseCoverObject> objects = new HashMap<>();
        private String savedBucket;
        private String savedObjectKey;

        @Override
        public void put(String bucket, String objectKey, InputStream data, long size, String contentType) throws Exception {
            savedBucket = bucket;
            savedObjectKey = objectKey;
            objects.put(objectKey, new StoredCourseCoverObject(data.readAllBytes(), contentType, size));
        }

        @Override
        public StoredCourseCoverObject get(String bucket, String objectKey) {
            return objects.get(objectKey);
        }
    }
}
