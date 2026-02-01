package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.SystemStatusApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "System status and health endpoints")
public class SystemStatusApiController {
    @Autowired
    private SystemStatusApiService systemStatusApiService;

    @GetMapping(value = "/system/status", produces = "application/json")
    @Operation(summary = "Get system status", description = "Retrieve current system status and health information")
    @ApiResponse(responseCode = "200", description = "System status retrieved successfully")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = systemStatusApiService.getSystemStatus();
        return ResponseEntity.ok(status);
    }
}
