package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseMaterialManagementService;
import org.ysu.ckqaback.course.dto.CourseMaterialResponse;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseMaterialsControllerWebMvcTest {

    private CourseMaterialManagementService materialManagementService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        materialManagementService = Mockito.mock(CourseMaterialManagementService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseMaterialsController(materialManagementService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldListCourseMaterialsWithOfficialRoute() throws Exception {
        given(materialManagementService.listMaterials(eq("os"), any(), any())).willReturn(new ApiPageData<>(
                List.of(response(7L, "book.pdf", "textbook", "done")),
                1,
                20,
                1,
                1
        ));

        mockMvc.perform(get(ApiPaths.COURSES + "/os/materials")
                        .param("keyword", "book")
                        .param("materialType", "textbook")
                        .param("parseStatus", "done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.items[0].fileName").value("book.pdf"))
                .andExpect(jsonPath("$.data.items[0].materialType").value("textbook"))
                .andExpect(jsonPath("$.data.items[0].parseStatus").value("done"))
                .andExpect(jsonPath("$.data.items[0].fileSize").value(1234));
    }

    @Test
    void shouldUploadPdfMaterial() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "book.pdf",
                "application/pdf",
                new byte[]{1, 2, 3, 4}
        );
        given(materialManagementService.uploadMaterial(eq("os"), any(), any(), any(), any()))
                .willReturn(response(8L, "操作系统讲义.pdf", "handout", "pending"));

        mockMvc.perform(multipart(ApiPaths.COURSES + "/os/materials")
                        .file(file)
                        .param("displayName", "操作系统讲义.pdf")
                        .param("materialType", "handout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.parseStatus").value("pending"));
    }

    @Test
    void shouldUpdateMaterialMetadata() throws Exception {
        given(materialManagementService.updateMaterial(eq("os"), eq(9L), any(), any()))
                .willReturn(response(9L, "新版教材.pdf", "textbook", "done"));

        mockMvc.perform(patch(ApiPaths.COURSES + "/os/materials/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "新版教材.pdf",
                                  "materialType": "textbook"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("新版教材.pdf"));
    }

    @Test
    void shouldDeleteMaterial() throws Exception {
        mockMvc.perform(delete(ApiPaths.COURSES + "/os/materials/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        then(materialManagementService).should().deleteMaterial(eq("os"), eq(9L), any());
    }

    private CourseMaterialResponse response(Long id, String fileName, String materialType, String parseStatus) {
        return CourseMaterialResponse.builder()
                .id(id)
                .materialId(id)
                .materialObjectId(17L)
                .courseId("os")
                .fileName(fileName)
                .displayName(fileName)
                .originalFileName(fileName)
                .materialType(materialType)
                .parseStatus(parseStatus)
                .fileMd5("abc")
                .fileSize(1234L)
                .mimeType("application/pdf")
                .uploadTime(LocalDateTime.of(2026, 5, 6, 16, 0))
                .build();
    }
}
