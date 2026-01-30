package com.workflow.engine.execution;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DataSourceProvider {

    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    private DataSource defaultDataSource;

    public DataSourceProvider(DataSource dataSource) {
        this.defaultDataSource = dataSource;
        this.dataSources.put("default", dataSource);
    }

    public void registerDataSource(String connectionId, DataSource dataSource) {
        dataSources.put(connectionId, dataSource);
    }

    public DataSource getDataSource(String connectionId) {
        if (connectionId == null || connectionId.trim().isEmpty()) {
            return defaultDataSource;
        }

        DataSource ds = dataSources.get(connectionId);
        if (ds == null) {
            throw new IllegalArgumentException("DataSource not found for connectionId: " + connectionId);
        }
        return ds;
    }

    public boolean hasDataSource(String connectionId) {
        if (connectionId == null || connectionId.trim().isEmpty()) {
            return true;
        }
        return dataSources.containsKey(connectionId);
    }
}
