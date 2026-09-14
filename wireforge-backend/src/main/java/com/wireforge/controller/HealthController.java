package com.wireforge.controller;

import com.wireforge.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "service", "WireForge Backend",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
