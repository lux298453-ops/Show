package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/design-folders")
@RequiredArgsConstructor
public class DesignFolderController {
    private final ProjectService projectService;

    @GetMapping
    public Result<List<String>> list() {
        return Result.ok(projectService.listDesignFolders());
    }
}
