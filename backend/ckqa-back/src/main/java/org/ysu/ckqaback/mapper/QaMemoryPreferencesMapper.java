package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.QaMemoryPreferences;

/**
 * 长期记忆偏好 Mapper。
 */
@Mapper
public interface QaMemoryPreferencesMapper extends BaseMapper<QaMemoryPreferences> {
}
