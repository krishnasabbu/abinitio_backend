package com.workflow.engine.execution;

import com.workflow.engine.model.DbConnectionConfig;
import com.workflow.engine.repository.DbConnectionRepository;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DataSourceProvider {

    private final DbConnectionRepository repository;
    private final ConcurrentHashMap<String, DataSource> cache = new ConcurrentHashMap<>();

    public DataSourceProvider(DbConnectionRepository repository) {
        this.repository = repository;
    }

    public DataSource getOrCreate(String connectionId) {
        return cache.computeIfAbsent(connectionId, this::create);
    }

    private DataSource create(String connectionId) {
        DbConnectionConfig cfg = repository.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "DB connection not found for connectionId: " + connectionId
            ));

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(cfg.jdbcUrl());
        ds.setUsername(cfg.username());
        ds.setPassword(cfg.password());
        ds.setDriverClassName(cfg.driverClass());
        return ds;
    }

    public void clearCache() {
        cache.clear();
    }

    public void invalidateCache(String connectionId) {
        cache.remove(connectionId);
    }

    public boolean hasDataSource(String connectionId) {
        if (connectionId == null || connectionId.trim().isEmpty()) {
            return false;
        }
        return repository.existsById(connectionId);
    }
}
