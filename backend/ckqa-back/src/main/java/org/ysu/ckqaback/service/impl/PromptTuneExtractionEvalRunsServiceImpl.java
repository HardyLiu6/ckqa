package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.PromptTuneExtractionEvalRunsMapper;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PromptTuneExtractionEvalRunsServiceImpl
        extends ServiceImpl<PromptTuneExtractionEvalRunsMapper, PromptTuneExtractionEvalRuns>
        implements PromptTuneExtractionEvalRunsService {

    private static final List<String> ACTIVE_STATUSES = List.of("pending", "running", "cancelling");

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findLatestByBuildRunId(Long buildRunId) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getBuildRunId, buildRunId)
                .orderByDesc(PromptTuneExtractionEvalRuns::getId)
                .last("LIMIT 1")
                .oneOpt();
    }

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findActiveByBuildRunId(Long buildRunId) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getBuildRunId, buildRunId)
                .in(PromptTuneExtractionEvalRuns::getStatus, ACTIVE_STATUSES)
                .orderByDesc(PromptTuneExtractionEvalRuns::getId)
                .last("LIMIT 1")
                .oneOpt();
    }

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findLatestSuccessByBuildRunId(Long buildRunId) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getBuildRunId, buildRunId)
                .eq(PromptTuneExtractionEvalRuns::getStatus, "success")
                .orderByDesc(PromptTuneExtractionEvalRuns::getId)
                .last("LIMIT 1")
                .oneOpt();
    }

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findLatestRecoverableScoringByBuildRunId(Long buildRunId) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getBuildRunId, buildRunId)
                .in(PromptTuneExtractionEvalRuns::getStatus, "failed", "cancelled")
                .eq(PromptTuneExtractionEvalRuns::getProgressStage, "scoring")
                .isNotNull(PromptTuneExtractionEvalRuns::getFinishedCandidates)
                .ne(PromptTuneExtractionEvalRuns::getFinishedCandidates, "[]")
                .orderByDesc(PromptTuneExtractionEvalRuns::getId)
                .last("LIMIT 1")
                .oneOpt();
    }

    @Override
    public List<PromptTuneExtractionEvalRuns> listStaleRunning(LocalDateTime heartbeatBefore) {
        return this.lambdaQuery()
                .eq(PromptTuneExtractionEvalRuns::getStatus, "running")
                .lt(PromptTuneExtractionEvalRuns::getLastHeartbeatAt, heartbeatBefore)
                .list();
    }

    @Override
    public List<PromptTuneExtractionEvalRuns> listAllActive() {
        return this.lambdaQuery()
                .in(PromptTuneExtractionEvalRuns::getStatus, ACTIVE_STATUSES)
                .orderByAsc(PromptTuneExtractionEvalRuns::getId)
                .list();
    }

    @Override
    public Optional<PromptTuneExtractionEvalRuns> findByIdWithoutReport(Long id) {
        // ServiceImpl.lambdaQuery 默认 select(*)；这里显式列字段排除 report_json。
        LambdaQueryWrapper<PromptTuneExtractionEvalRuns> q = new LambdaQueryWrapper<>();
        q.select(
                PromptTuneExtractionEvalRuns::getId,
                PromptTuneExtractionEvalRuns::getBuildRunId,
                PromptTuneExtractionEvalRuns::getKnowledgeBaseId,
                PromptTuneExtractionEvalRuns::getSelectedCandidateIds,
                PromptTuneExtractionEvalRuns::getSeed,
                PromptTuneExtractionEvalRuns::getStatus,
                PromptTuneExtractionEvalRuns::getProgressStage,
                PromptTuneExtractionEvalRuns::getExtractingCandidateId,
                PromptTuneExtractionEvalRuns::getFinishedCandidates,
                PromptTuneExtractionEvalRuns::getCandidateFailures,
                PromptTuneExtractionEvalRuns::getEvalDir,
                PromptTuneExtractionEvalRuns::getErrorMessage,
                PromptTuneExtractionEvalRuns::getTriggeredByUserId,
                PromptTuneExtractionEvalRuns::getStartedAt,
                PromptTuneExtractionEvalRuns::getFinishedAt,
                PromptTuneExtractionEvalRuns::getLastHeartbeatAt,
                PromptTuneExtractionEvalRuns::getCreatedAt,
                PromptTuneExtractionEvalRuns::getUpdatedAt
        );
        q.eq(PromptTuneExtractionEvalRuns::getId, id);
        return Optional.ofNullable(this.getOne(q));
    }

    @Override
    public PromptTuneExtractionEvalRuns getRequiredById(Long id) {
        PromptTuneExtractionEvalRuns entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_RUN_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "评分任务不存在: " + id
            );
        }
        return entity;
    }
}
