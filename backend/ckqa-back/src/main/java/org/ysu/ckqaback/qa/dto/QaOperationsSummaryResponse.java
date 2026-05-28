package org.ysu.ckqaback.qa.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 问答运维列表全库聚合统计响应。
 *
 * <p>所有字段均按当前查询条件（不含分页参数）在数据库层面聚合得出，
 * 用于前端运维概览卡片展示，避免前端用「当前页」做误导性统计。
 */
@Getter
@Setter
@NoArgsConstructor
public class QaOperationsSummaryResponse {

    /** 总样本数（命中筛选条件的全部记录数）。 */
    private long total;

    /** 成功任务数。 */
    private long success;

    /** 失败或失效任务数（taskStatus = failed | stale）。 */
    private long failed;

    /** 路由置信度为「低置信」的任务数。 */
    private long lowConfidence;

    /** 复核优先级非 normal 的任务数（待复核）。 */
    private long needReview;

    public QaOperationsSummaryResponse(long total, long success, long failed, long lowConfidence, long needReview) {
        this.total = total;
        this.success = success;
        this.failed = failed;
        this.lowConfidence = lowConfidence;
        this.needReview = needReview;
    }
}
