package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.entity.CourseRouteDecisions;
import org.ysu.ckqaback.mapper.CourseRouteDecisionsMapper;
import org.ysu.ckqaback.service.CourseRouteDecisionsService;

@Service
public class CourseRouteDecisionsServiceImpl
        extends ServiceImpl<CourseRouteDecisionsMapper, CourseRouteDecisions>
        implements CourseRouteDecisionsService {
}
