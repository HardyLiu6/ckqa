package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.ysu.ckqaback.entity.QaRetrievalHits;
import org.ysu.ckqaback.integration.graphrag.GraphRagSourceSnapshot;
import org.ysu.ckqaback.mapper.QaRetrievalHitsMapper;
import org.ysu.ckqaback.qa.dto.QaSourceResponse;
import org.ysu.ckqaback.service.QaRetrievalHitsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 问答命中文档表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class QaRetrievalHitsServiceImpl extends ServiceImpl<QaRetrievalHitsMapper, QaRetrievalHits> implements QaRetrievalHitsService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public void replaceHits(Long retrievalLogId, List<GraphRagSourceSnapshot> sources) {
        if (retrievalLogId == null) {
            return;
        }
        LambdaQueryWrapper<QaRetrievalHits> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(QaRetrievalHits::getRetrievalLogId, retrievalLogId);
        remove(deleteWrapper);

        if (CollectionUtils.isEmpty(sources)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        List<QaRetrievalHits> hits = sources.stream()
                .map(source -> toHit(retrievalLogId, source, now))
                .toList();
        saveBatch(hits);
    }

    @Override
    public Map<Long, List<QaSourceResponse>> findSourcesByRetrievalLogIds(List<Long> retrievalLogIds) {
        if (CollectionUtils.isEmpty(retrievalLogIds)) {
            return Map.of();
        }
        LambdaQueryWrapper<QaRetrievalHits> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(QaRetrievalHits::getRetrievalLogId, retrievalLogIds)
                .orderByAsc(QaRetrievalHits::getRetrievalLogId)
                .orderByAsc(QaRetrievalHits::getRankPosition)
                .orderByAsc(QaRetrievalHits::getId);

        Map<Long, List<QaSourceResponse>> grouped = new LinkedHashMap<>();
        for (QaRetrievalHits hit : list(queryWrapper)) {
            grouped.computeIfAbsent(hit.getRetrievalLogId(), ignored -> new java.util.ArrayList<>())
                    .add(QaSourceResponse.fromEntity(hit));
        }
        return grouped;
    }

    private QaRetrievalHits toHit(Long retrievalLogId, GraphRagSourceSnapshot source, LocalDateTime now) {
        QaRetrievalHits hit = new QaRetrievalHits();
        hit.setRetrievalLogId(retrievalLogId);
        hit.setDocumentKey(source.documentKey());
        hit.setChunkId(source.chunkId());
        hit.setSourceRef(source.ref());
        hit.setSourceFile(source.sourceFile());
        hit.setHeadingPath(source.headingPath());
        hit.setPageStart(source.pageStart());
        hit.setPageEnd(source.pageEnd());
        hit.setSnippet(source.snippet());
        hit.setRankPosition(source.rank());
        hit.setCreatedAt(now);
        return hit;
    }
}
