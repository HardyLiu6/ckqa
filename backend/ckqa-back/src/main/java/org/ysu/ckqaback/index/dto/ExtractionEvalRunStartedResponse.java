package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /extraction-eval 响应：评分任务已被排队/运行。
 *
 * <p>同步返回 evalRunId 让前端立即开始轮询；reusedActiveRun=true 时表示
 * 复用已存在的同 buildRun 活动任务（决策 4），前端无需启动新任务。</p>
 */
@Getter
@Builder
public class ExtractionEvalRunStartedResponse {

    private final Long evalRunId;
    private final Long buildRunId;
    private final List<String> selectedCandidateIds;

    /** pending / running / cancelling。 */
    private final String status;

    /** 是否复用已存在的活动评分任务。 */
    private final Boolean reusedActiveRun;

    private final LocalDateTime startedAt;

    /** 推荐的轮询间隔（毫秒），前端按这个周期调用 status 接口。 */
    private final Integer recommendedPollingIntervalMillis;
}
