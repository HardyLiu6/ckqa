package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.QaRetrievalHits;
import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.integration.graphrag.GraphRagSourceSnapshot;
import org.ysu.ckqaback.qa.dto.QaSourceResponse;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 问答命中文档表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface QaRetrievalHitsService extends IService<QaRetrievalHits> {

    void replaceHits(Long retrievalLogId, List<GraphRagSourceSnapshot> sources);

    Map<Long, List<QaSourceResponse>> findSourcesByRetrievalLogIds(List<Long> retrievalLogIds);
}
