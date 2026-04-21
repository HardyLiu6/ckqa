package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.UserRoles;

/**
 * <p>
 * 用户角色关联表 Mapper 接口
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Mapper
public interface UserRolesMapper extends BaseMapper<UserRoles> {

}
