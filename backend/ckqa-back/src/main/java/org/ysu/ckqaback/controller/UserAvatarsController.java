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
import org.ysu.ckqaback.user.UserAvatarService;
import org.ysu.ckqaback.user.dto.UserAvatarContent;

import java.time.Duration;

/**
 * 用户头像只读访问接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.API_V1 + "/user-avatars")
public class UserAvatarsController {

    private final UserAvatarService userAvatarService;

    @GetMapping("/{fileName}")
    public ResponseEntity<ByteArrayResource> getUserAvatar(@PathVariable String fileName) {
        UserAvatarContent content = userAvatarService.load(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.getContentType()))
                .contentLength(content.getFileSize())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
                .body(new ByteArrayResource(content.getBytes()));
    }
}
