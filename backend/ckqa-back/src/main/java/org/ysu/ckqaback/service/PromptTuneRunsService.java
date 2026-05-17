package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.PromptTuneRuns;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 提示词自动调优运行表 Service。
 */
public interface PromptTuneRunsService extends IService<PromptTuneRuns> {

    /**
     * 找到与 cacheKey 完全匹配的 success 记录（最新一条）。
     */
    Optional<PromptTuneRuns> findLatestSuccessByCacheKey(String cacheKey);

    /**
     * 找到指定 cacheKey 当前正在 pending / running 的记录。
     */
    Optional<PromptTuneRuns> findActiveByCacheKey(String cacheKey);

    /**
     * 找到指定 build run 关联的最新一条调优记录。
     */
    Optional<PromptTuneRuns> findLatestByBuildRunId(Long buildRunId);

    /**
     * 找到指定知识库的所有调优记录（按创建时间倒序）。
     */
    List<PromptTuneRuns> listByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 把超过阈值仍处于 running 状态的记录标 failed，返回被恢复的记录列表。
     */
    List<PromptTuneRuns> recoverStaleRunningRuns(Duration staleThreshold);

    /**
     * 启动后兜底：把 status=running 但 finished_at 已经被填过的记录翻成 failed。
     * <p>这类记录是 worker 已经走过 {@link #markFailed} / {@link #markSuccess} 终态写入，
     * 但因 tailer 滞后回写覆盖了 status 字段（丢失更新）所产生的脏状态。
     * 它和 {@link #recoverStaleRunningRuns} 阈值兜底互补：终态字段已写但 status 没翻的，
     * 不需要等 40 分钟超时，启动后立即修正。</p>
     *
     * @return 被修复的记录列表
     */
    List<PromptTuneRuns> recoverInconsistentRunningRuns();

    /**
     * 写「开始运行」终态：把 pending → running，置 started_at/last_heartbeat_at/updated_at。
     * <p>仅设需要的字段；通过 WHERE status IN ('pending','running') 守护，避免覆盖
     * 其它终态。重复调用幂等。</p>
     *
     * @return 被更新的行数
     */
    int markRunning(Long id);

    /**
     * 写「进度推进」：仅设 progress_stage / latest_logs / last_heartbeat_at / updated_at。
     * <p>WHERE status='running' 守护，避免在 worker 已 mark success/failed 后被
     * tailer 滞后回写覆盖终态字段。</p>
     *
     * @return 被更新的行数（0 表示已不在 running，调用方应停止后续写）
     */
    int updateProgressStage(Long id, String stage, String latestLogs);

    /**
     * 写「日志追加」心跳：仅设 latest_logs / last_heartbeat_at / updated_at。
     * <p>WHERE status='running' 守护，与 {@link #updateProgressStage} 同理。</p>
     *
     * @return 被更新的行数（0 表示已不在 running）
     */
    int appendLatestLogs(Long id, String latestLogs);

    /**
     * 写「成功」终态：仅设 status/progress_stage/prompt_sha256/latest_logs/finished_at/
     * last_heartbeat_at/updated_at。
     * <p>WHERE status IN ('pending','running') 守护，确保只能从未完成态翻为 success。</p>
     *
     * @return 被更新的行数
     */
    int markSuccess(Long id, String promptSha256, String latestLogs);

    /**
     * 写「失败」终态：仅设 status/progress_stage/error_message/latest_logs(可选)/
     * finished_at/last_heartbeat_at/updated_at。
     * <p>WHERE status IN ('pending','running') 守护。{@code latestLogs} 为 null 时
     * 不覆盖现有 latest_logs。</p>
     *
     * @return 被更新的行数
     */
    int markFailed(Long id, String errorMessage, String latestLogs);

    /**
     * 必须能取到 ID 对应的记录，否则抛 NOT_FOUND。
     */
    PromptTuneRuns getRequiredById(Long id);
}
