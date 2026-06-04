package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.qa.dto.QaSessionMessageCount;
import org.ysu.ckqaback.qa.dto.QaSessionStatsResponse;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 问答会话表 Mapper 接口
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Mapper
public interface QaSessionsMapper extends BaseMapper<QaSessions> {

    /**
     * 按筛选条件在数据库层聚合统计正式会话的真实口径，供「问答记录」统计卡片使用。
     * 统计不受分页影响，反映命中条件的全部历史。
     */
    QaSessionStatsResponse selectFormalSessionStats(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("courseId") String courseId,
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("favorite") Boolean favorite,
            @Param("keyword") String keyword);

    /**
     * 批量查询当前页会话的真实消息数，避免前端对每张历史卡片逐个请求消息列表。
     */
    List<QaSessionMessageCount> selectMessageCountsBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

}
