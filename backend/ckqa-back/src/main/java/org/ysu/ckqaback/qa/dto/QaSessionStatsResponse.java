package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 学生端问答记录统计响应。
 *
 * <p>所有字段均按当前查询条件（不含分页参数）在数据库层面聚合得出，
 * 用于「问答记录」页统计卡片展示，避免前端用「当前页」做误导性统计。
 */
@Getter
@Setter
@NoArgsConstructor
public class QaSessionStatsResponse {

    /** 命中筛选条件的全部会话数。 */
    private long totalSessions;

    /** 这些会话下的全部消息数。 */
    private long totalMessages;

    /** 这些会话涉及的去重课程数。 */
    private long courseCount;

    /** 命中筛选条件的收藏会话数。 */
    private long favoriteCount;

    public QaSessionStatsResponse(long totalSessions, long totalMessages, long courseCount, long favoriteCount) {
        this.totalSessions = totalSessions;
        this.totalMessages = totalMessages;
        this.courseCount = courseCount;
        this.favoriteCount = favoriteCount;
    }
}
