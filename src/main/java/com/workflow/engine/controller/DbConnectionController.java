package com.workflow.engine.controller;

import com.workflow.engine.execution.DataSourceProvider;
import com.workflow.engine.model.DbConnectionConfig;
import com.workflow.engine.repository.DbConnectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/db-connections")
public class DbConnectionController {

    private final DbConnectionRepository repository;
    private final DataSourceProvider dataSourceProvider;

    public DbConnectionController(
        DbConnectionRepository repository,
        DataSourceProvider dataSourceProvider
    ) {
        this.repository = repository;
        this.dataSourceProvider = dataSourceProvider;
    }

    @PostMapping
    public ResponseEntity<DbConnectionConfig> create(@RequestBody DbConnectionRequest request) {
        if (request.id() == null || request.id().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (repository.existsById(request.id())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        DbConnectionConfig config = new DbConnectionConfig(
            request.id(),
            request.name(),
            request.connectionType(),
            request.jdbcUrl(),
            request.username(),
            request.password(),
            request.driverClass(),
            LocalDateTime.now()
        );

        repository.save(config);
        dataSourceProvider.clearCache();

        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }

    @GetMapping
    public ResponseEntity<List<DbConnectionConfig>> getAll() {
        List<DbConnectionConfig> connections = repository.findAll();
        return ResponseEntity.ok(connections);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DbConnectionConfig> getById(@PathVariable String id) {
        return repository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DbConnectionConfig> update(
        @PathVariable String id,
        @RequestBody DbConnectionRequest request
    ) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        DbConnectionConfig config = new DbConnectionConfig(
            id,
            request.name(),
            request.connectionType(),
            request.jdbcUrl(),
            request.username(),
            request.password(),
            request.driverClass(),
            null
        );

        repository.update(config);
        dataSourceProvider.clearCache();

        return repository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        repository.delete(id);
        dataSourceProvider.clearCache();

        return ResponseEntity.noContent().build();
    }
}
