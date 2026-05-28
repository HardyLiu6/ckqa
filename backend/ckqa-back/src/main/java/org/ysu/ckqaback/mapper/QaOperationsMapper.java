package org.ysu.ckqaback.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.ysu.ckqaback.qa.dto.QaOperationsQueryRequest;
import org.ysu.ckqaback.qa.dto.QaOperationsSummaryResponse;
import org.ysu.ckqaback.qa.ops.QaOperationFeedbackRow;
import org.ysu.ckqaback.qa.ops.QaOperationLogRow;
import org.ysu.ckqaback.qa.ops.QaOperationSourceRow;

import java.util.List;

/**
 * 问答运维聚合查询 Mapper。
 */
@Mapper
public interface QaOperationsMapper {

    long countLogs(
            @Param("request") QaOperationsQueryRequest request,
            @Param("currentUserId") Long currentUserId,
            @Param("adminScope") boolean adminScope
    );

    /**
     * 在数据库层按当前筛选条件聚合统计，避免前端拿当前页数据做误导性统计。
     */
    QaOperationsSummaryResponse selectSummary(
            @Param("request") QaOperationsQueryRequest request,
            @Param("currentUserId") Long currentUserId,
            @Param("adminScope") boolean adminScope
    );

    List<QaOperationLogRow> selectLogs(
            @Param("request") QaOperationsQueryRequest request,
            @Param("currentUserId") Long currentUserId,
            @Param("adminScope") boolean adminScope,
            @Param("offset") long offset,
            @Param("size") long size
    );

    QaOperationLogRow selectLogDetail(
            @Param("retrievalLogId") Long retrievalLogId,
            @Param("currentUserId") Long currentUserId,
            @Param("adminScope") boolean adminScope
    );

    List<QaOperationFeedbackRow> selectFeedbackByLogIds(@Param("retrievalLogIds") List<Long> retrievalLogIds);

    List<QaOperationSourceRow> selectSourcesByLogId(@Param("retrievalLogId") Long retrievalLogId);
}
