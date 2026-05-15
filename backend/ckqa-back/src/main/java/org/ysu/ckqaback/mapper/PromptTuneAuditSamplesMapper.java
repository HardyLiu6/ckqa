package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;

/**
 * 手动调优标注样本表 Mapper。
 */
@Mapper
public interface PromptTuneAuditSamplesMapper extends BaseMapper<PromptTuneAuditSamples> {
}
