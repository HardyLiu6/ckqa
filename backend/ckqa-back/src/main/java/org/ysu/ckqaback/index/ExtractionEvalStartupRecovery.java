package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务启动时的评分任务恢复扫描。
 *
 * <p>决策 7：所有 active 任务在服务重启后必然与原进程脱钩，按状态分三类恢复：</p>
 * <ul>
 *   <li>{@code pending} → 重新 dispatch（worker 接管后会自然切 running）</li>
 *   <li>{@code running} → 标 {@code failed}，error_message 注明心跳时间</li>
 *   <li>{@code cancelling} → 标 {@code cancelled}，等价于"用户的取消请求被服务终结时收尾"</li>
 * </ul>
 *
 * <p>不再像 Phase 4 PromptTuneStartupRecovery 那样仅扫 stale running，
 * 因为 after-commit dispatch 留下的 pending 与用户请求取消后崩溃留下的 cancelling
 * 都会卡住后续 trigger 复用逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class ExtractionEvalStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExtractionEvalStartupRecovery.class);

    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final ExtractionEvalWorker worker;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<PromptTuneExtractionEvalRuns> active = evalRunsService.listAllActive();
            if (active.isEmpty()) return;
            log.warn("启动时发现 {} 个失联评分任务，按状态分类恢复", active.size());
            for (PromptTuneExtractionEvalRuns run : active) {
                String status = run.getStatus();
                if ("pending".equalsIgnoreCase(status)) {
                    log.info("评分任务 {} 处于 pending，重新派发", run.getId());
                    worker.dispatch(run.getId());
                } else if ("running".equalsIgnoreCase(status)) {
                    run.setStatus("failed");
                    run.setProgressStage("done");
                    run.setErrorMessage("服务重启时被中断（last heartbeat: " + run.getLastHeartbeatAt() + "）");
                    run.setFinishedAt(LocalDateTime.now());
                    run.setUpdatedAt(LocalDateTime.now());
                    evalRunsService.updateById(run);
                    log.warn("评分任务 {} 处于 running，标记为 failed", run.getId());
                } else if ("cancelling".equalsIgnoreCase(status)) {
                    run.setStatus("cancelled");
                    run.setProgressStage("done");
                    run.setErrorMessage("服务重启时被中断（用户已请求取消）");
                    run.setFinishedAt(LocalDateTime.now());
                    run.setUpdatedAt(LocalDateTime.now());
                    evalRunsService.updateById(run);
                    log.warn("评分任务 {} 处于 cancelling，标记为 cancelled", run.getId());
                }
            }
        } catch (RuntimeException exception) {
            log.error("评分任务启动恢复失败，跳过", exception);
        }
    }
}
