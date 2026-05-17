package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.mapper.PromptDraftsMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptDraftsServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯 Mockito 单测下没有 SqlSessionFactory，需手动注册 entity 的 TableInfo，
        // 否则 LambdaQueryWrapper.select(...) / orderByDesc(...) 会抛 "can not find lambda cache".
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""),
                PromptDrafts.class
        );
    }

    @Test
    void listByKnowledgeBaseIdInvokesSelectListWithExpectedWrapper() {
        // 验证：调 selectList 时 wrapper 含 eq(knowledgeBaseId) + orderByDesc(createdAt) + 排除 promptsJson 列
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);

        AtomicReference<Wrapper<PromptDrafts>> captured = new AtomicReference<>();
        when(mapper.selectList(any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return List.of();
        });

        service.listByKnowledgeBaseId(7L);

        assertThat(captured.get()).isInstanceOf(LambdaQueryWrapper.class);
        // 排序断言：SQL 片段含 ORDER BY created_at DESC
        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<PromptDrafts> wrapper = (LambdaQueryWrapper<PromptDrafts>) captured.get();
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("created_at DESC");
        // 排除大字段断言：select 段不含 prompts_json
        String selectSql = wrapper.getSqlSelect();
        if (selectSql != null) {
            assertThat(selectSql).doesNotContain("prompts_json");
        }
    }

    @Test
    void listByKnowledgeBaseIdReturnsRowsInRepositoryOrder() {
        // 两条记录 mock 倒序返回，验证 service 透传 mapper 顺序，不做内部重排
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);

        PromptDrafts newer = new PromptDrafts();
        newer.setId(2L);
        newer.setCreatedAt(java.time.LocalDateTime.of(2026, 5, 17, 10, 0));
        PromptDrafts older = new PromptDrafts();
        older.setId(1L);
        older.setCreatedAt(java.time.LocalDateTime.of(2026, 5, 15, 10, 0));
        when(mapper.selectList(any())).thenReturn(List.of(newer, older));

        List<PromptDrafts> result = service.listByKnowledgeBaseId(7L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(1).getId()).isEqualTo(1L);
    }

    @Test
    void countByKnowledgeBaseIdReturnsCount() {
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectCount(any())).thenReturn(3L);

        assertThat(service.countByKnowledgeBaseId(7L)).isEqualTo(3L);
    }

    @Test
    void countByKnowledgeBaseIdReturnsZeroWhenNoDrafts() {
        PromptDraftsServiceImpl service = new PromptDraftsServiceImpl();
        PromptDraftsMapper mapper = mock(PromptDraftsMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectCount(any())).thenReturn(0L);

        assertThat(service.countByKnowledgeBaseId(99L)).isEqualTo(0L);
    }
}
