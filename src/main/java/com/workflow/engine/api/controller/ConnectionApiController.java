package com.workflow.engine.api.controller;

import com.workflow.engine.api.dto.DatabaseConnectionDto;
import com.workflow.engine.api.dto.KafkaConnectionDto;
import com.workflow.engine.api.service.ConnectionApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConnectionApiController {
    @Autowired
    private ConnectionApiService connectionApiService;

    @GetMapping("/database-connections")
    public ResponseEntity<List<DatabaseConnectionDto>> getAllDatabaseConnections() {
        List<DatabaseConnectionDto> connections = connectionApiService.getAllDatabaseConnections();
        return ResponseEntity.ok(connections);
    }

    @PostMapping("/database-connections")
    public ResponseEntity<Map<String, String>> createDatabaseConnection(@RequestBody DatabaseConnectionDto request) {
        String id = connectionApiService.createDatabaseConnection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/database-connections/{id}")
    public ResponseEntity<Void> updateDatabaseConnection(@PathVariable String id, @RequestBody DatabaseConnectionDto request) {
        connectionApiService.updateDatabaseConnection(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/database-connections/{id}")
    public ResponseEntity<Void> deleteDatabaseConnection(@PathVariable String id) {
        connectionApiService.deleteDatabaseConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-db-connection/{id}")
    public ResponseEntity<Map<String, Object>> testDatabaseConnection(@PathVariable String id) {
        Map<String, Object> result = connectionApiService.testDatabaseConnection(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/kafka-connections")
    public ResponseEntity<List<KafkaConnectionDto>> getAllKafkaConnections() {
        List<KafkaConnectionDto> connections = connectionApiService.getAllKafkaConnections();
        return ResponseEntity.ok(connections);
    }

    @PostMapping("/kafka-connections")
    public ResponseEntity<Map<String, String>> createKafkaConnection(@RequestBody KafkaConnectionDto request) {
        String id = connectionApiService.createKafkaConnection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/kafka-connections/{id}")
    public ResponseEntity<Void> updateKafkaConnection(@PathVariable String id, @RequestBody KafkaConnectionDto request) {
        connectionApiService.updateKafkaConnection(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/kafka-connections/{id}")
    public ResponseEntity<Void> deleteKafkaConnection(@PathVariable String id) {
        connectionApiService.deleteKafkaConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-kafka-connection/{id}")
    public ResponseEntity<Map<String, Object>> testKafkaConnection(@PathVariable String id) {
        Map<String, Object> result = connectionApiService.testKafkaConnection(id);
        return ResponseEntity.ok(result);
    }
}
