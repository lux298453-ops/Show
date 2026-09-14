package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 素材库接口：提供素材二进制流与素材列表。
 *
 * 路由：
 *   GET /api/assets/{assetId}          → 素材文件（二进制流）
 *   GET /api/assets/list               → 全部素材列表
 *   GET /api/assets/categories         → 素材分类
 *   GET /api/assets/random/{category}  → 随机返回某类素材
 */
@Slf4j
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /** 素材二进制流（供前端 <img src="/api/assets/{assetId}"> 直接引用） */
    @GetMapping("/{assetId}")
    public ResponseEntity<Resource> getAsset(@PathVariable String assetId) {
        Resource resource = assetService.getAssetResource(assetId);
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "素材不存在: " + assetId);
        }
        String contentType = resolveContentType(assetId, resource);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(resource);
    }

    /** 全部素材列表 */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(assetService.getAssetList());
    }

    /** 素材分类 */
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.ok(assetService.getAssetCategories());
    }

    /** 随机返回某类素材（category 可为元素类型 avatar/image/icon/background/effect 或分类名） */
    @GetMapping("/random/{category}")
    public Result<Map<String, Object>> random(@PathVariable String category) {
        String assetId = assetService.getRandomAsset(category);
        if (assetId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该分类的素材: " + category);
        }
        var meta = assetService.getAssetMeta(assetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", meta.assetId());
        result.put("category", meta.category());
        result.put("fileName", meta.fileName());
        result.put("description", meta.description());
        result.put("url", assetService.getAssetUrl(assetId));
        return Result.ok(result);
    }

    private static String resolveContentType(String assetId, Resource resource) {
        String name = resource.getFilename() != null ? resource.getFilename() : assetId;
        if (name.endsWith(".svg")) return MediaType.valueOf("image/svg+xml").toString();
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG.toString();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG.toString();
        if (name.endsWith(".gif")) return MediaType.IMAGE_GIF.toString();
        if (name.endsWith(".webp")) return "image/webp";
        return MediaType.APPLICATION_OCTET_STREAM.toString();
    }
}
