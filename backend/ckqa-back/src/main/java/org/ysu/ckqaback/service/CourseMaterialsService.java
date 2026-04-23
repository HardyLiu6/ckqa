package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.CourseMaterials;

import java.util.List;

/**
 * <p>
 * 课程资料关系表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-23
 */
public interface CourseMaterialsService extends IService<CourseMaterials> {

    /**
     * 根据主键查询课程资料，不存在时抛出业务异常。
     *
     * @param id 课程资料ID
     * @return 课程资料
     */
    CourseMaterials getRequiredById(Long id);

    /**
     * 抢占解析开始状态。
     *
     * @param id 课程资料ID
     * @return 是否成功从 pending/failed 切换为 processing
     */
    boolean claimParseStart(Long id);

    /**
     * 若资料仍处于 processing，则标记解析失败。
     *
     * @param id 课程资料ID
     * @param errorMessage 解析错误信息
     * @return 是否成功标记失败
     */
    boolean markParseFailedIfStillProcessing(Long id, String errorMessage);

    /**
     * 查询课程下的资料列表。
     *
     * @param courseId 课程ID
     * @return 按上传时间和创建时间倒序排列的课程资料列表
     */
    List<CourseMaterials> listByCourseId(String courseId);
}
