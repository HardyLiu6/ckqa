package org.ysu.ckqaback.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.QaSessionSummaries;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 问答会话滚动摘要 Mapper。
 */
@Mapper
public interface QaSessionSummariesMapper extends BaseMapper<QaSessionSummaries> {
}
