package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.PromptTuneRuns;

/**
 * 提示词自动调优运行表 Mapper。
 */
@Mapper
public interface PromptTuneRunsMapper extends BaseMapper<PromptTuneRuns> {
}
