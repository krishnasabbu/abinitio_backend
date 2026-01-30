package com.workflow.engine.controller;

public record DbConnectionRequest(
    String id,
    String name,
    String connectionType,
    String jdbcUrl,
    String username,
    String password,
    String driverClass
) {}
