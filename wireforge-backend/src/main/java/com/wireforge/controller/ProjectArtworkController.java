package com.wireforge.controller;

import com.wireforge.service.ProjectArtworkService;
import com.wireforge.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class ProjectArtworkController {
    private final ProjectArtworkService artwork;
    private final ProjectService projects;

    /**
     * 角色与背景插画输出接口：开启 HTTP 强缓存（1年 + immutable + ETag）
     * 图片 URL 基于 SHA-256 digest 寻址，内容不可变。
     * 浏览器在首次请求后直接从本地磁盘/内存缓存以 0ms 读取，避免刷新或全屏演示时重复越洋加载。
     */
    @GetMapping("/api/assets/artwork/{projectId}/{digest}.png")
    public ResponseEntity<Resource> image(
            @PathVariable long projectId,
            @PathVariable String digest,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        projects.getProject(projectId);
        String etag = "\"" + digest + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304)
                    .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                    .eTag(etag)
                    .build();
        }
        Resource image = artwork.image(projectId, digest);
        if (!image.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .eTag(etag)
                .body(image);
    }
}
