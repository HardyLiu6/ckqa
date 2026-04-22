package org.ysu.ckqaback.course;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.PdfFilesService;

import java.util.List;

/**
 * 课程维度查询服务。
 */
@Service
@RequiredArgsConstructor
public class CourseLookupService {

    private final PdfFilesService pdfFilesService;
    private final KnowledgeBasesService knowledgeBasesService;

    public List<CoursePdfFileSummaryResponse> listCoursePdfFiles(String courseId) {
        return pdfFilesService.listByCourseId(courseId).stream()
                .map(CoursePdfFileSummaryResponse::fromEntity)
                .toList();
    }

    public List<KnowledgeBaseSummaryResponse> listKnowledgeBases(String courseId) {
        return knowledgeBasesService.listByCourseId(courseId).stream()
                .map(KnowledgeBaseSummaryResponse::fromEntity)
                .toList();
    }
}
