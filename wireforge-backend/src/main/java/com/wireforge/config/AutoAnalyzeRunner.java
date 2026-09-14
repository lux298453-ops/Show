package com.wireforge.config;

import com.wireforge.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后自动把设计稿目录中的新图片转成可交互线稿原型。
 * 控制开关: wireforge.auto-analyze（默认 true）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoAnalyzeRunner implements CommandLineRunner {

    private final ProjectService projectService;

    @Value("${wireforge.auto-analyze:true}")
    private boolean autoAnalyze;

    @Override
    public void run(String... args) {
        if (!autoAnalyze) {
            log.info("wireforge.auto-analyze=false，跳过自动生成");
            return;
        }
        log.info("自动生成开始：扫描设计稿目录…");
        try {
            var project = projectService.autoGenerate();
            if (project != null) {
                log.info("自动生成完成: 项目 [{}] (id={})", project.getName(), project.getId());
            }
        } catch (Exception e) {
            log.error("自动生成失败: {}", e.getMessage(), e);
        }
    }
}