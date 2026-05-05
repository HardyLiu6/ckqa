package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.IndexRuns;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.ActiveIndexRunResponse;
import org.ysu.ckqaback.service.IndexArtifactsService;
import org.ysu.ckqaback.service.IndexRunsService;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;

import java.util.List;

/**
 * 知识库激活索引切换服务。
 */
@Service
@RequiredArgsConstructor
public class ActiveIndexRunService {

    private final IndexRunsService indexRunsService;
    private final IndexArtifactsService artifactsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final KnowledgeBaseBuildRunsService buildRunsService;

    @Transactional
    public ActiveIndexRunResponse activate(Long knowledgeBaseId, Long indexRunId, boolean manual) {
        IndexRuns run = indexRunsService.getRequiredById(indexRunId);
        if (!knowledgeBaseId.equals(run.getKnowledgeBaseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "索引运行不属于目标知识库");
        }
        if (!"success".equals(run.getStatus())) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT, "只能激活成功的索引运行");
        }

        List<IndexArtifacts> artifacts = artifactsService.listByIndexRunId(indexRunId);
        requireReadyArtifact(artifacts, "output_dir");
        requireReadyArtifact(artifacts, "lancedb");

        knowledgeBasesService.updateActiveIndexRunId(knowledgeBaseId, indexRunId);
        buildRunsService.clearActiveIndexRunMarkers(knowledgeBaseId);
        if (run.getBuildRunId() != null) {
            KnowledgeBaseBuildRuns buildRun = buildRunsService.getRequiredById(run.getBuildRunId());
            buildRun.setActiveIndexRunId(indexRunId);
            buildRunsService.updateById(buildRun);
        }

        return ActiveIndexRunResponse.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .activeIndexRunId(indexRunId)
                .buildRunId(run.getBuildRunId())
                .build();
    }

    private void requireReadyArtifact(List<IndexArtifacts> artifacts, String artifactType) {
        boolean ready = artifacts.stream().anyMatch(artifact ->
                artifactType.equals(artifact.getArtifactType()) && "ready".equals(artifact.getArtifactStatus())
        );
        if (!ready) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT, "索引产物不完整");
        }
    }
}
