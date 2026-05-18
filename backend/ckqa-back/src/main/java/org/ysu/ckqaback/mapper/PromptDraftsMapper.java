package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.PromptDrafts;

/**
 * 手动调优历史草稿表 Mapper。
 */
@Mapper
public interface PromptDraftsMapper extends BaseMapper<PromptDrafts> {
}
