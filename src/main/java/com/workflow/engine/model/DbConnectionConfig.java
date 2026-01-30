package com.workflow.engine.model;

import java.time.LocalDateTime;

public record DbConnectionConfig(
    String id,
    String name,
    String connectionType,
    String jdbcUrl,
    String username,
    String password,
    String driverClass,
    LocalDateTime createdAt
) {}
