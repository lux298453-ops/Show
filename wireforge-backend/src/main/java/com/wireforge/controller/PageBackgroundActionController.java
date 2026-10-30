package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.entity.Element;
import com.wireforge.service.PageBackgroundActionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/pages/{pageId}/background-return")
@RequiredArgsConstructor
public class PageBackgroundActionController {
    private final PageBackgroundActionService service;
    public record Settings(@NotNull Boolean enabled,String clientId) {}

    @PutMapping
    public Result<Element> configure(@PathVariable Long projectId,@PathVariable Long pageId,@Valid @RequestBody Settings settings) {
        return Result.ok(service.configure(projectId,pageId,settings.enabled(),settings.clientId()));
    }
}
