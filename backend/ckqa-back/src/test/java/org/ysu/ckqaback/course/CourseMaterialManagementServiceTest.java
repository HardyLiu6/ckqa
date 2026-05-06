package org.ysu.ckqaback.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.CoursesService;
import org.ysu.ckqaback.service.MaterialObjectsService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseMaterialManagementServiceTest {

    private CourseMaterialsService courseMaterialsService;
    private MaterialObjectsService materialObjectsService;
    private CoursesService coursesService;
    private CourseAccessService courseAccessService;
    private CourseCoverObjectStorage storage;
    private CourseMaterialManagementService service;

    @BeforeEach
    void setUp() {
        courseMaterialsService = mock(CourseMaterialsService.class);
        materialObjectsService = mock(MaterialObjectsService.class);
        coursesService = mock(CoursesService.class);
        courseAccessService = mock(CourseAccessService.class);
        storage = mock(CourseCoverObjectStorage.class);
        CourseMaterialProperties properties = new CourseMaterialProperties();
        service = new CourseMaterialManagementService(
                courseMaterialsService,
                materialObjectsService,
                coursesService,
                courseAccessService,
                properties,
                storage
        );
    }

    @Test
    void shouldRejectSinglePdfLargerThan200MbBeforeReadingBytes() throws Exception {
        when(coursesService.count(any())).thenReturn(1L);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(200L * 1024L * 1024L + 1L);
        when(file.getOriginalFilename()).thenReturn("large.pdf");
        when(file.getContentType()).thenReturn("application/pdf");

        assertThatThrownBy(() -> service.uploadMaterial("os", file, "大文件", "textbook", "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("课程资料文件不能超过200MB");

        verify(file, never()).getBytes();
        verify(storage, never()).put(any(), any(), any(), anyLong(), any());
    }
}
