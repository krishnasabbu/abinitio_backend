package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.SystemStatusApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemStatusApiController {
    @Autowired
    private SystemStatusApiService systemStatusApiService;

    @GetMapping("/system/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = systemStatusApiService.getSystemStatus();
        return ResponseEntity.ok(status);
    }
}
