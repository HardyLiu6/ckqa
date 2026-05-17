package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 04 步评分运行存取服务。
 */
public interface PromptTuneExtractionEvalRunsService extends IService<PromptTuneExtractionEvalRuns> {

    /**
     * 取指定 buildRun 下最近一条评分记录（无论状态），按 id 倒序。
     * <p>用于 GET /extraction-eval/status；前端只看 build run 维度的最新一次。</p>
     */
    Optional<PromptTuneExtractionEvalRuns> findLatestByBuildRunId(Long buildRunId);

    /**
     * 取指定 buildRun 下处于 active（pending/running/cancelling）的评分记录。
     * <p>用于 trigger 时复用已运行任务（决策 4：返回该任务 id 而不是抛错）。</p>
     */
    Optional<PromptTuneExtractionEvalRuns> findActiveByBuildRunId(Long buildRunId);

    /**
     * 取指定 buildRun 下最近一条 status=success 的评分记录。
     * <p>用于失败/取消终态下让前端"查看上次评分结果"——即便当前最新 evalRun 已 failed/cancelled，
     * 历史 success 报告仍可访问。无 success 记录时返回 empty。</p>
     */
    Optional<PromptTuneExtractionEvalRuns> findLatestSuccessByBuildRunId(Long buildRunId);

    /**
     * 列出心跳过期的 running 任务（用于启动恢复时把卡死的运行任务标记 failed）。
     */
    List<PromptTuneExtractionEvalRuns> listStaleRunning(LocalDateTime heartbeatBefore);

    /**
     * 列出所有当前处于 active 状态（pending / running / cancelling）的任务，
     * 不限心跳——专供 {@code ExtractionEvalStartupRecovery} 在服务启动时使用。
     * <p>启动场景下任何 active 任务都已与正在运行的进程脱钩：</p>
     * <ul>
     *   <li>pending：worker 派发前进程崩溃 → 永远不会被消费，必须显式恢复。</li>
     *   <li>running：心跳是否过期都不重要——服务都重启了，不可能还在跑。</li>
     *   <li>cancelling：用户已请求取消，原 worker 已不复存在，需要直接落 cancelled 终态。</li>
     * </ul>
     */
    List<PromptTuneExtractionEvalRuns> listAllActive();

    /**
     * 列表查询时不带 report_json，避免拉满大字段。
     */
    Optional<PromptTuneExtractionEvalRuns> findByIdWithoutReport(Long id);

    /**
     * 必查；为空时抛 NotFound。
     */
    PromptTuneExtractionEvalRuns getRequiredById(Long id);
}
