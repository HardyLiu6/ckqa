package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.CourseRouteProfiles;

import java.util.Optional;

public interface CourseRouteProfilesService extends IService<CourseRouteProfiles> {

    Optional<CourseRouteProfiles> findActiveByCourseAndModel(String courseId, String embeddingModel, int dimensions);
}
