package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ysu.ckqaback.entity.RolePermissions;

/**
 * <p>
 * 角色权限关联表 Mapper 接口
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Mapper
public interface RolePermissionsMapper extends BaseMapper<RolePermissions> {

}
