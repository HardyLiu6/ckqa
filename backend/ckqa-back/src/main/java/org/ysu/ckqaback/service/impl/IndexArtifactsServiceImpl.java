package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.IndexArtifactsMapper;
import org.ysu.ckqaback.service.IndexArtifactsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 索引产物表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class IndexArtifactsServiceImpl extends ServiceImpl<IndexArtifactsMapper, IndexArtifacts> implements IndexArtifactsService {

    @Override
    public IndexArtifacts getRequiredById(Long id) {
        IndexArtifacts artifact = getById(id);
        if (artifact == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "索引产物不存在");
        }
        return artifact;
    }

    @Override
    public List<IndexArtifacts> listByIndexRunId(Long indexRunId) {
        LambdaQueryWrapper<IndexArtifacts> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IndexArtifacts::getIndexRunId, indexRunId)
                .orderByAsc(IndexArtifacts::getArtifactType)
                .orderByAsc(IndexArtifacts::getId);
        return list(wrapper);
    }

    @Override
    public void removeByIndexRunId(Long indexRunId) {
        LambdaQueryWrapper<IndexArtifacts> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IndexArtifacts::getIndexRunId, indexRunId);
        remove(wrapper);
    }

    @Override
    public IndexArtifacts markDeleted(Long id) {
        LambdaUpdateWrapper<IndexArtifacts> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(IndexArtifacts::getId, id)
                .set(IndexArtifacts::getArtifactStatus, "deleted");
        baseMapper.update(null, wrapper);
        return getRequiredById(id);
    }
}
