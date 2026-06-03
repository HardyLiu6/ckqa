package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.qa.dto.QaSessionQueryRequest;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
}
