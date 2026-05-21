package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.CourseRouteProfiles;
import org.ysu.ckqaback.mapper.CourseRouteProfilesMapper;
import org.ysu.ckqaback.service.CourseRouteProfilesService;

import java.util.Optional;

@Service
public class CourseRouteProfilesServiceImpl
        extends ServiceImpl<CourseRouteProfilesMapper, CourseRouteProfiles>
        implements CourseRouteProfilesService {

    @Override
    public Optional<CourseRouteProfiles> findActiveByCourseAndModel(String courseId, String embeddingModel, int dimensions) {
        CourseRouteProfiles profile = getOne(new LambdaQueryWrapper<CourseRouteProfiles>()
                .eq(CourseRouteProfiles::getCourseId, courseId)
                .eq(CourseRouteProfiles::getEmbeddingModel, embeddingModel)
                .eq(CourseRouteProfiles::getEmbeddingDimensions, dimensions)
                .eq(CourseRouteProfiles::getStatus, "active")
                .last("LIMIT 1"));
        return Optional.ofNullable(profile);
    }
}
