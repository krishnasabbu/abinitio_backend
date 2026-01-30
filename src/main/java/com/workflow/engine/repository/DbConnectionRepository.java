package com.workflow.engine.repository;

import com.workflow.engine.model.DbConnectionConfig;

import java.util.List;
import java.util.Optional;

public interface DbConnectionRepository {

    Optional<DbConnectionConfig> findById(String connectionId);

    List<DbConnectionConfig> findAll();

    void save(DbConnectionConfig config);

    void update(DbConnectionConfig config);

    void delete(String connectionId);

    boolean existsById(String connectionId);
}
