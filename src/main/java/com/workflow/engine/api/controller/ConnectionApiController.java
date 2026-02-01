package com.workflow.engine.api.controller;

import com.workflow.engine.api.dto.DatabaseConnectionDto;
import com.workflow.engine.api.dto.KafkaConnectionDto;
import com.workflow.engine.api.service.ConnectionApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Connections", description = "Operations for managing database and Kafka connections")
public class ConnectionApiController {
    @Autowired
    private ConnectionApiService connectionApiService;

    @GetMapping("/database-connections")
    @Operation(summary = "List database connections", description = "Retrieve all configured database connections")
    @ApiResponse(responseCode = "200", description = "Database connections retrieved successfully")
    public ResponseEntity<List<DatabaseConnectionDto>> getAllDatabaseConnections() {
        List<DatabaseConnectionDto> connections = connectionApiService.getAllDatabaseConnections();
        return ResponseEntity.ok(connections);
    }

    @PostMapping("/database-connections")
    @Operation(summary = "Create database connection", description = "Create a new database connection configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Database connection created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, String>> createDatabaseConnection(@RequestBody DatabaseConnectionDto request) {
        String id = connectionApiService.createDatabaseConnection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/database-connections/{id}")
    @Operation(summary = "Update database connection", description = "Update an existing database connection configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Database connection updated successfully"),
        @ApiResponse(responseCode = "404", description = "Connection not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> updateDatabaseConnection(
            @Parameter(description = "The connection ID", required = true)
            @PathVariable String id,
            @RequestBody DatabaseConnectionDto request) {
        connectionApiService.updateDatabaseConnection(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/database-connections/{id}")
    @Operation(summary = "Delete database connection", description = "Delete a database connection configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Database connection deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Connection not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteDatabaseConnection(
            @Parameter(description = "The connection ID", required = true)
            @PathVariable String id) {
        connectionApiService.deleteDatabaseConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-db-connection/{id}")
    @Operation(summary = "Test database connection", description = "Test the connectivity of a database connection")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connection test result"),
        @ApiResponse(responseCode = "404", description = "Connection not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> testDatabaseConnection(
            @Parameter(description = "The connection ID", required = true)
            @PathVariable String id) {
        Map<String, Object> result = connectionApiService.testDatabaseConnection(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/kafka-connections")
    @Operation(summary = "List Kafka connections", description = "Retrieve all configured Kafka connections")
    @ApiResponse(responseCode = "200", description = "Kafka connections retrieved successfully")
    public ResponseEntity<List<KafkaConnectionDto>> getAllKafkaConnections() {
        List<KafkaConnectionDto> connections = connectionApiService.getAllKafkaConnections();
        return ResponseEntity.ok(connections);
    }

    @PostMapping("/kafka-connections")
    @Operation(summary = "Create Kafka connection", description = "Create a new Kafka connection configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Kafka connection created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, String>> createKafkaConnection(@RequestBody KafkaConnectionDto request) {
        String id = connectionApiService.createKafkaConnection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/kafka-connections/{id}")
    @Operation(summary = "Update Kafka connection", description = "Update an existing Kafka connection configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kafka connection updated successfully"),
        @ApiResponse(responseCode = "404", description = "Connection not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> updateKafkaConnection(
            @Parameter(description = "The connection ID", required = true)
            @PathVariable String id,
            @RequestBody KafkaConnectionDto request) {
        connectionApiService.updateKafkaConnection(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/kafka-connections/{id}")
    @Operation(summary = "Delete Kafka connection", description = "Delete a Kafka connection configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Kafka connection deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Connection not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteKafkaConnection(
            @Parameter(description = "The connection ID", required = true)
            @PathVariable String id) {
        connectionApiService.deleteKafkaConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-kafka-connection/{id}")
    @Operation(summary = "Test Kafka connection", description = "Test the connectivity of a Kafka connection")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connection test result"),
        @ApiResponse(responseCode = "404", description = "Connection not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> testKafkaConnection(
            @Parameter(description = "The connection ID", required = true)
            @PathVariable String id) {
        Map<String, Object> result = connectionApiService.testKafkaConnection(id);
        return ResponseEntity.ok(result);
    }
}
