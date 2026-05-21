package org.ysu.ckqaback.course.routing;

import org.ysu.ckqaback.entity.Courses;
import org.ysu.ckqaback.entity.KnowledgeBases;

import java.util.List;

public interface CourseProfileHintProvider {

    List<CourseProfileHint> loadHints(Courses course, List<KnowledgeBases> knowledgeBases);
}
