package org.ysu.ckqaback.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.course.CourseCoverService;
import org.ysu.ckqaback.course.dto.CourseCoverContent;

import java.time.Duration;

/**
 * 课程封面只读访问接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/course-covers")
public class CourseCoversController {

    private final CourseCoverService courseCoverService;

    @GetMapping("/{fileName}")
    public ResponseEntity<ByteArrayResource> getCourseCover(@PathVariable String fileName) {
        CourseCoverContent content = courseCoverService.load(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.getContentType()))
                .contentLength(content.getFileSize())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
                .body(new ByteArrayResource(content.getBytes()));
    }
}
