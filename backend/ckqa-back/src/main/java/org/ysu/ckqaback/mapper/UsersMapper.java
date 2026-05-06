package org.ysu.ckqaback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.ysu.ckqaback.entity.Users;

import java.util.List;

/**
 * <p>
 * 平台用户表 Mapper 接口
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Mapper
public interface UsersMapper extends BaseMapper<Users> {

    Page<Users> selectUserPage(
            Page<Users> page,
            @Param("username") String username,
            @Param("status") String status,
            @Param("roleCode") String roleCode,
            @Param("keyword") String keyword
    );

    long countUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    List<String> selectRoleCodes(@Param("userId") Long userId);

    List<String> selectPermissionCodes(@Param("userId") Long userId);
}
