package org.ysu.ckqaback.course.routing;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.CourseMaterials;
import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.KbDocuments;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.service.CourseMaterialsService;
import org.ysu.ckqaback.service.KbDocumentsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CourseProfileTextBuilderTest {

    @Test
    void shouldIncludeKbDocumentTitlesInStableProfileText() {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        KbDocumentsService kbDocumentsService = mock(KbDocumentsService.class);
        CourseProfileHintProvider hintProvider = mock(CourseProfileHintProvider.class);
        CourseProfileTextBuilder builder = new CourseProfileTextBuilder(
                knowledgeBasesService,
                courseMaterialsService,
                kbDocumentsService,
                hintProvider
        );

        given(knowledgeBasesService.listByCourseId("os")).willReturn(List.of(knowledgeBase()));
        given(courseMaterialsService.listByCourseId("os")).willReturn(List.of(material()));
        given(kbDocumentsService.list(any(Wrapper.class)))
                .willReturn(List.of(kbDocument("第 3 章 进程与线程"), kbDocument("第 4 章 调度")));
        given(hintProvider.loadHints(any(Courses.class), any(List.class)))
                .willReturn(List.of(
                        new CourseProfileHint(
                                "泛化小节",
                                Collections.nCopies(120, "重复词")
                        ),
                        new CourseProfileHint(
                                "第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构",
                                List.of("快表", "TLB", "地址转换")
                        ),
                        new CourseProfileHint(
                                "第六章 输入输出系统 > 6.3 中断机构和中断处理程序",
                                List.of("I/O", "设备驱动程序", "中断", "轮询")
                        )
                ));

        CourseProfileSnapshot snapshot = builder.build(course());
        CourseProfileSnapshot repeated = builder.build(course());

        assertThat(snapshot.profileText()).contains("课程名称：操作系统");
        assertThat(snapshot.profileText()).contains("知识库：操作系统知识库、进程、线程、死锁");
        assertThat(snapshot.profileText()).contains("章节标题：第 3 章 进程与线程、第 4 章 调度");
        assertThat(snapshot.profileText()).contains("课程画像章节来源：泛化小节、第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构、第六章 输入输出系统 > 6.3 中断机构和中断处理程序");
        assertThat(snapshot.profileText()).contains("课程画像关键词：");
        assertThat(snapshot.profileText()).contains("重复词", "快表", "TLB", "地址转换", "I/O", "设备驱动程序", "中断", "轮询");
        assertThat(snapshot.profileText()).contains("课程资料：操作系统教材.pdf");
        assertThat(snapshot.profileHash()).hasSize(64);
        assertThat(repeated.profileHash()).isEqualTo(snapshot.profileHash());
    }

    @Test
    void shouldPreserveKeywordsFromLaterTechnicalHintsWhenEarlyHintsAreDense() {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseMaterialsService courseMaterialsService = mock(CourseMaterialsService.class);
        KbDocumentsService kbDocumentsService = mock(KbDocumentsService.class);
        CourseProfileHintProvider hintProvider = mock(CourseProfileHintProvider.class);
        CourseProfileTextBuilder builder = new CourseProfileTextBuilder(
                knowledgeBasesService,
                courseMaterialsService,
                kbDocumentsService,
                hintProvider
        );

        given(knowledgeBasesService.listByCourseId("os")).willReturn(List.of(knowledgeBase()));
        given(courseMaterialsService.listByCourseId("os")).willReturn(List.of());
        given(kbDocumentsService.list(any(Wrapper.class))).willReturn(List.of());
        List<CourseProfileHint> denseHints = new java.util.ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            denseHints.add(new CourseProfileHint("早期章节" + index, numberedKeywords("early-" + index + "-", 20)));
        }
        denseHints.add(new CourseProfileHint(
                "第四章 存储器管理 > 4.5 分页存储管理方式 > 4.5.2 地址变换机构",
                List.of("快表", "TLB", "地址转换")
        ));
        denseHints.add(new CourseProfileHint(
                "第六章 输入输出系统 > 6.4 设备驱动程序 > 6.4.3 对I/O设备的控制方式",
                List.of("I/O", "设备驱动程序", "中断", "轮询")
        ));
        given(hintProvider.loadHints(any(Courses.class), any(List.class))).willReturn(denseHints);

        CourseProfileSnapshot snapshot = builder.build(course());

        assertThat(snapshot.profileText()).contains("快表", "TLB", "I/O", "设备驱动程序", "中断", "轮询");
    }

    private Courses course() {
        Courses course = new Courses();
        course.setCourseId("os");
        course.setCourseName("操作系统");
        course.setDescription("进程、线程、内存管理");
        course.setTags("[\"进程\", \"调度\"]");
        return course;
    }

    private KnowledgeBases knowledgeBase() {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(12L);
        knowledgeBase.setName("操作系统知识库");
        knowledgeBase.setDescription("进程、线程、死锁");
        return knowledgeBase;
    }

    private CourseMaterials material() {
        CourseMaterials material = new CourseMaterials();
        material.setId(3L);
        material.setDisplayName("操作系统教材.pdf");
        return material;
    }

    private KbDocuments kbDocument(String title) {
        KbDocuments document = new KbDocuments();
        document.setTitle(title);
        return document;
    }

    private List<String> numberedKeywords(String prefix, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> prefix + index)
                .toList();
    }
}
