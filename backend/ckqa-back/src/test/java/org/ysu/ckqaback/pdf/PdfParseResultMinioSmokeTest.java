package org.ysu.ckqaback.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.CourseCoverProperties;
import org.ysu.ckqaback.course.MinioCourseCoverObjectStorage;
import org.ysu.ckqaback.controller.PdfFilesController;
import org.ysu.ckqaback.entity.ParseResults;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.integration.locks.DatabaseNamedLockService;
import org.ysu.ckqaback.integration.pdf.PdfIngestOrchestrator;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.ParseResultsService;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本地 MinIO 解析产物联调 smoke。
 *
 * <p>默认跳过，避免普通单元测试依赖本机 MinIO。需要手工设置
 * CKQA_RUN_MINIO_PARSE_RESULT_SMOKE=true 和 CKQA_SMOKE_PARSE_RESULT_OBJECT_KEY 后运行。</p>
 */
@EnabledIfEnvironmentVariable(named = "CKQA_RUN_MINIO_PARSE_RESULT_SMOKE", matches = "true")
class PdfParseResultMinioSmokeTest {

    @Test
    void shouldPreviewAndDownloadRealParseResultObjectFromMinio() throws Exception {
        SmokeConfig config = SmokeConfig.fromEnvironment();
        assertThat(config.objectKey())
                .as("请设置 CKQA_SMOKE_PARSE_RESULT_OBJECT_KEY 为 MinIO 中已存在的解析产物对象 key")
                .isNotBlank();

        ParseResults parseResult = new ParseResults();
        parseResult.setId(config.resultId());
        parseResult.setCourseMaterialId(config.materialId());
        parseResult.setCourseId(config.courseId());
        parseResult.setResultType(config.resultType());
        parseResult.setFileName(config.fileName());
        parseResult.setMinioBucket(config.bucket());
        parseResult.setMinioObjectKey(config.objectKey());

        ParseResultsService parseResultsService = mock(ParseResultsService.class);
        given(parseResultsService.getById(config.resultId())).willReturn(parseResult);

        PdfWorkflowService workflowService = new PdfWorkflowService(
                mock(CourseMaterialsService.class),
                parseResultsService,
                mock(PdfIngestOrchestrator.class),
                mock(DatabaseNamedLockService.class),
                new MinioCourseCoverObjectStorage(config.toStorageProperties()),
                mock(PdfParseTaskDispatcher.class),
                mock(CourseAccessService.class)
        );

        MockMvc mockMvc = createMockMvc(workflowService);
        String path = ApiPaths.PDF_FILES + "/" + config.materialId() + "/results/" + config.resultId();

        MvcResult preview = mockMvc.perform(get(path + "/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"" + config.fileName() + "\""))
                .andReturn();

        assertThat(preview.getResponse().getContentAsByteArray()).isNotEmpty();
        assertExpectedContent(preview, config);

        MvcResult download = mockMvc.perform(get(path + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + config.fileName() + "\""))
                .andReturn();

        assertThat(download.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(download.getResponse().getContentAsByteArray())
                .containsExactly(preview.getResponse().getContentAsByteArray());
    }

    private static MockMvc createMockMvc(PdfWorkflowService workflowService) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new PdfFilesController(
                        workflowService,
                        mock(PdfParseStreamTokenService.class),
                        mock(PdfParseEventStreamService.class)
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static void assertExpectedContent(MvcResult preview, SmokeConfig config) throws Exception {
        if (!StringUtils.hasText(config.expectedContains())) {
            return;
        }
        String content = preview.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).contains(config.expectedContains());
    }

    private record SmokeConfig(
            Long materialId,
            Long resultId,
            String courseId,
            String resultType,
            String bucket,
            String objectKey,
            String fileName,
            String expectedContains,
            String endpoint,
            String accessKey,
            String secretKey,
            boolean secure
    ) {

        static SmokeConfig fromEnvironment() {
            String objectKey = env("CKQA_SMOKE_PARSE_RESULT_OBJECT_KEY", "");
            return new SmokeConfig(
                    longEnv("CKQA_SMOKE_PARSE_RESULT_MATERIAL_ID", 1L),
                    longEnv("CKQA_SMOKE_PARSE_RESULT_RESULT_ID", 1L),
                    env("CKQA_SMOKE_PARSE_RESULT_COURSE_ID", "local-smoke"),
                    env("CKQA_SMOKE_PARSE_RESULT_TYPE", "mineru"),
                    firstEnv("course-artifacts", "CKQA_SMOKE_PARSE_RESULT_BUCKET", "COURSE_MATERIAL_BUCKET", "COURSE_COVER_BUCKET"),
                    objectKey,
                    env("CKQA_SMOKE_PARSE_RESULT_FILE_NAME", fileNameFromObjectKey(objectKey)),
                    env("CKQA_SMOKE_PARSE_RESULT_EXPECT_CONTAINS", ""),
                    env("MINIO_ENDPOINT", "localhost:9000"),
                    firstEnv("admin", "MINIO_ACCESS_KEY", "MINIO_ROOT_USER"),
                    firstEnv("12345678", "MINIO_SECRET_KEY", "MINIO_ROOT_PASSWORD"),
                    Boolean.parseBoolean(env("MINIO_SECURE", "false"))
            );
        }

        CourseCoverProperties toStorageProperties() {
            CourseCoverProperties properties = new CourseCoverProperties();
            properties.setEndpoint(endpoint);
            properties.setAccessKey(accessKey);
            properties.setSecretKey(secretKey);
            properties.setSecure(secure);
            return properties;
        }

        private static Long longEnv(String name, Long fallback) {
            String value = env(name, "");
            if (!StringUtils.hasText(value)) {
                return fallback;
            }
            return Long.parseLong(value);
        }

        private static String firstEnv(String fallback, String... names) {
            for (String name : names) {
                Optional<String> value = Optional.ofNullable(System.getenv(name)).filter(StringUtils::hasText);
                if (value.isPresent()) {
                    return value.get();
                }
            }
            return fallback;
        }

        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return StringUtils.hasText(value) ? value : fallback;
        }

        private static String fileNameFromObjectKey(String objectKey) {
            if (!StringUtils.hasText(objectKey)) {
                return "content_list.json";
            }
            int index = objectKey.lastIndexOf('/');
            return index >= 0 ? objectKey.substring(index + 1) : objectKey;
        }
    }
}
