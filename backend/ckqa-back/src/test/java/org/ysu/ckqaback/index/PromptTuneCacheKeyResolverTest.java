package org.ysu.ckqaback.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.MaterialObjects;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.MaterialObjectsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PromptTuneCacheKeyResolverTest {

    private CourseMaterialsService courseMaterialsService;
    private MaterialObjectsService materialObjectsService;
    private PromptTuneCacheKeyResolver resolver;

    @BeforeEach
    void setUp() {
        courseMaterialsService = mock(CourseMaterialsService.class);
        materialObjectsService = mock(MaterialObjectsService.class);
        resolver = new PromptTuneCacheKeyResolver(courseMaterialsService, materialObjectsService);
    }

    @Test
    void resolve_singleMaterial_producesDeterministicKey() {
        stubMaterial(7L, "os", 70L, "md5_a");

        var ctxA = resolver.resolve("[7]", "os");
        var ctxB = resolver.resolve("[7]", "os");

        assertThat(ctxA.cacheKey()).isEqualTo(ctxB.cacheKey());
        assertThat(ctxA.materialIds()).containsExactly(7L);
        assertThat(ctxA.materialMd5()).containsEntry(7L, "md5_a");
        assertThat(ctxA.cacheKey()).hasSize(64);  // sha256 hex
    }

    @Test
    void resolve_orderInvariant_sameMaterialsSameKey() {
        stubMaterial(7L, "os", 70L, "md5_a");
        stubMaterial(8L, "os", 71L, "md5_b");
        stubMaterial(9L, "os", 72L, "md5_c");

        String keyA = resolver.resolve("[7,8,9]", "os").cacheKey();
        String keyB = resolver.resolve("[9,7,8]", "os").cacheKey();

        assertThat(keyA).isEqualTo(keyB);
    }

    @Test
    void resolve_sameMaterialsDifferentMd5_differentKey() {
        stubMaterial(7L, "os", 70L, "md5_a");
        String keyA = resolver.resolve("[7]", "os").cacheKey();

        // 模拟资料被重新解析：md5 变了
        stubMaterial(7L, "os", 70L, "md5_a_v2");
        String keyB = resolver.resolve("[7]", "os").cacheKey();

        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    void resolve_emptySelection_throwsBadRequest() {
        assertThatThrownBy(() -> resolver.resolve("[]", "os"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先在第 1 步选择课程资料");
    }

    @Test
    void resolve_blankSelection_throwsBadRequest() {
        assertThatThrownBy(() -> resolver.resolve(null, "os"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先在第 1 步选择课程资料");
    }

    @Test
    void resolve_materialBelongsToOtherCourse_throwsBadRequest() {
        stubMaterial(7L, "ds", 70L, "md5_a");

        assertThatThrownBy(() -> resolver.resolve("[7]", "os"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前知识库课程");
    }

    @Test
    void resolve_missingMd5_throwsBadRequest() {
        CourseMaterials material = new CourseMaterials();
        material.setId(7L);
        material.setCourseId("os");
        material.setMaterialObjectId(70L);
        given(courseMaterialsService.getRequiredById(7L)).willReturn(material);
        MaterialObjects object = new MaterialObjects();
        object.setId(70L);
        object.setFileMd5(null);
        given(materialObjectsService.getById(70L)).willReturn(object);

        assertThatThrownBy(() -> resolver.resolve("[7]", "os"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少文件指纹");
    }

    @Test
    void resolve_invalidJson_throwsBadRequest() {
        assertThatThrownBy(() -> resolver.resolve("[1, broken", "os"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("资料选择快照格式非法");
    }

    private void stubMaterial(Long materialId, String courseId, Long objectId, String md5) {
        CourseMaterials material = new CourseMaterials();
        material.setId(materialId);
        material.setCourseId(courseId);
        material.setMaterialObjectId(objectId);
        given(courseMaterialsService.getRequiredById(materialId)).willReturn(material);

        MaterialObjects object = new MaterialObjects();
        object.setId(objectId);
        object.setFileMd5(md5);
        given(materialObjectsService.getById(objectId)).willReturn(object);
    }
}
