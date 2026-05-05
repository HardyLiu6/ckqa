package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.IndexArtifacts;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 索引产物表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface IndexArtifactsService extends IService<IndexArtifacts> {

    IndexArtifacts getRequiredById(Long id);

    java.util.List<IndexArtifacts> listByIndexRunId(Long indexRunId);

    void removeByIndexRunId(Long indexRunId);

    IndexArtifacts markDeleted(Long id);
}
