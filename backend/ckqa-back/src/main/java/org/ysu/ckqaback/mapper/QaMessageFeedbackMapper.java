package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.QaMessageFeedback;

/**
 * 问答消息反馈 Mapper。
 */
@Mapper
public interface QaMessageFeedbackMapper extends BaseMapper<QaMessageFeedback> {
}
