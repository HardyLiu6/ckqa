package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.mapper.QaSessionsMapper;
import org.ysu.ckqaback.qa.dto.QaSessionMessageCount;
import org.ysu.ckqaback.qa.dto.QaSessionQueryRequest;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class QaSessionsServiceImplTest {

    @BeforeAll
    static void initLambdaCache() {
        // 纯单元测试里没有 SqlSessionFactory，手动注册实体让 lambda 列解析可用。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), QaSessions.class);
    }

    /**
     * 刚创建、还没有任何消息的会话 last_message_at 为 NULL；MySQL 在 DESC 下把 NULL 排到最后，
     * 会让这些"刚提问"的会话沉到列表底部，被侧栏 size=20 截断而看不见。
     * 排序键必须用 COALESCE(last_message_at, created_at)，让无消息会话退回按创建时间排序。
     */
    @Test
    void pageFormalSessionsOrdersByCoalescedRecencySoSessionsWithoutMessagesUseCreatedAt() {
        AtomicReference<Wrapper<QaSessions>> captured = new AtomicReference<>();
        QaSessionsServiceImpl service = new QaSessionsServiceImpl() {
            @Override
            public <E extends IPage<QaSessions>> E page(E page, Wrapper<QaSessions> queryWrapper) {
                captured.set(queryWrapper);
                return page;
            }
        };

        QaSessionQueryRequest request = new QaSessionQueryRequest();
        request.setPage(1L);
        request.setSize(20L);

        service.pageFormalSessions(42L, request);

        String sql = captured.get().getSqlSegment();
        assertThat(sql)
                .as("无消息会话应按 created_at 兜底排序,而不是被当成最旧沉底")
                .contains("COALESCE(last_message_at, created_at)");
        assertThat(sql.toUpperCase()).contains("DESC");
    }

    @Test
    void pageFormalSessionsCanFilterFavoritesAndOrderOldestFirst() {
        AtomicReference<Wrapper<QaSessions>> captured = new AtomicReference<>();
        QaSessionsServiceImpl service = new QaSessionsServiceImpl() {
            @Override
            public <E extends IPage<QaSessions>> E page(E page, Wrapper<QaSessions> queryWrapper) {
                captured.set(queryWrapper);
                return page;
            }
        };

        QaSessionQueryRequest request = new QaSessionQueryRequest();
        request.setFavorite(true);
        request.setSort("oldest");

        service.pageFormalSessions(42L, request);

        String sql = captured.get().getSqlSegment();
        assertThat(sql).contains("is_favorite");
        assertThat(sql).contains("COALESCE(last_message_at, created_at)");
        assertThat(sql.toUpperCase()).contains("ASC");
    }

    @Test
    void pageFormalSessionsCanOrderByMessageCountInDatabase() {
        AtomicReference<Wrapper<QaSessions>> captured = new AtomicReference<>();
        QaSessionsServiceImpl service = new QaSessionsServiceImpl() {
            @Override
            public <E extends IPage<QaSessions>> E page(E page, Wrapper<QaSessions> queryWrapper) {
                captured.set(queryWrapper);
                return page;
            }
        };

        QaSessionQueryRequest request = new QaSessionQueryRequest();
        request.setSort("messages");

        service.pageFormalSessions(42L, request);

        String sql = captured.get().getSqlSegment();
        assertThat(sql).contains("qa_messages");
        assertThat(sql.toUpperCase()).contains("COUNT");
        assertThat(sql.toUpperCase()).contains("DESC");
    }

    @Test
    void pageFormalSessionsAttachesMessageCountsForCurrentPage() {
        QaSessionsMapper mapper = mock(QaSessionsMapper.class);
        QaSessionsServiceImpl service = new QaSessionsServiceImpl() {
            @Override
            public <E extends IPage<QaSessions>> E page(E page, Wrapper<QaSessions> queryWrapper) {
                page.setRecords(List.of(session(5L), session(6L)));
                page.setTotal(2L);
                return page;
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
        given(mapper.selectMessageCountsBySessionIds(argThat(ids -> ids.containsAll(List.of(5L, 6L)))))
                .willReturn(List.of(new QaSessionMessageCount(5L, 3L)));

        QaSessionQueryRequest request = new QaSessionQueryRequest();
        request.setPage(1L);
        request.setSize(20L);

        ApiPageData<QaSessionResponse> result = service.pageFormalSessions(42L, request);

        assertThat(result.getItems()).extracting(QaSessionResponse::getMessageCount).containsExactly(3L, 0L);
        then(mapper).should().selectMessageCountsBySessionIds(argThat(ids -> ids.containsAll(List.of(5L, 6L))));
    }

    private static QaSessions session(Long id) {
        QaSessions session = new QaSessions();
        session.setId(id);
        session.setSessionCode("qa-" + id);
        session.setUserId(42L);
        session.setCourseId("os");
        session.setKnowledgeBaseId(3L);
        session.setIndexRunId(17L);
        session.setSessionType("formal");
        session.setTitle("会话 " + id);
        session.setStatus("active");
        session.setIsFavorite(false);
        session.setCreatedAt(LocalDateTime.of(2026, 6, 4, 10, 0));
        return session;
    }
}
