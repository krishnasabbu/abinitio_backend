package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

/**
 * Executor for writing data to a database table via JDBC.
 * Supports insert, upsert, and truncate-insert modes with batch processing.
 */
@Component
public class DBSinkExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(DBSinkExecutor.class);

    private final DataSourceProvider dataSourceProvider;
    private boolean truncateExecuted = false;

    public DBSinkExecutor(DataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public String getNodeType() {
        return "DBSink";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            logger.debug("nodeId={}, Using BufferedItemReader for port 'in'", nodeId);
            return new BufferedItemReader(executionId, nodeId, "in", routingCtx.getRoutingContext().getBufferStore());
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        String nodeId = context.getNodeDefinition().getId();
        JsonNode config = context.getNodeDefinition().getConfig();

        String connectionId = config.get("connectionId").asText();
        String tableName = config.get("tableName").asText();
        String mode = config.has("mode") ? config.get("mode").asText().toLowerCase() : "insert";
        int batchSize = config.has("batchSize") ? config.get("batchSize").asInt() : 1000;

        DataSource dataSource = dataSourceProvider.getOrCreate(connectionId);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        if ("truncate-insert".equals(mode) && !truncateExecuted) {
            truncateTable(dataSource, tableName);
            truncateExecuted = true;
        }

        if ("insert".equals(mode) || "truncate-insert".equals(mode)) {
            return createInsertWriter(jdbcTemplate, tableName, batchSize);
        } else if ("upsert".equals(mode)) {
            return createUpsertWriter(jdbcTemplate, dataSource, tableName, batchSize);
        } else {
            throw new IllegalArgumentException("DBSink mode '" + mode + "' is not supported");
        }
    }

    private ItemWriter<Map<String, Object>> createInsertWriter(
        NamedParameterJdbcTemplate jdbcTemplate,
        String tableName,
        int batchSize
    ) {
        return items -> {
            if (items.isEmpty()) {
                return;
            }

            logger.info("nodeId={}, Writing {} items to table '{}' via insert", "DBSink", items.size(), tableName);

            List<Map<String, Object>> batch = new ArrayList<>();
            Set<String> columns = null;

            for (Map<String, Object> item : items) {
                if (columns == null) {
                    columns = new LinkedHashSet<>(item.keySet());
                }
                batch.add(item);

                if (batch.size() >= batchSize) {
                    executeBatchInsert(jdbcTemplate, tableName, columns, batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                executeBatchInsert(jdbcTemplate, tableName, columns, batch);
            }
        };
    }

    private void executeBatchInsert(
        NamedParameterJdbcTemplate jdbcTemplate,
        String tableName,
        Set<String> columns,
        List<Map<String, Object>> batch
    ) {
        String sql = buildInsertSql(tableName, columns);
        Map<String, Object>[] batchValues = batch.toArray(new Map[0]);
        jdbcTemplate.batchUpdate(sql, batchValues);
    }

    private String buildInsertSql(String tableName, Set<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");

        List<String> columnList = new ArrayList<>(columns);
        sql.append(String.join(", ", columnList));
        sql.append(") VALUES (");

        List<String> placeholders = new ArrayList<>();
        for (String column : columnList) {
            placeholders.add(":" + column);
        }
        sql.append(String.join(", ", placeholders));
        sql.append(")");

        return sql.toString();
    }

    private ItemWriter<Map<String, Object>> createUpsertWriter(
        NamedParameterJdbcTemplate jdbcTemplate,
        DataSource dataSource,
        String tableName,
        int batchSize
    ) {
        List<String> primaryKeys = detectPrimaryKeys(dataSource, tableName);

        if (primaryKeys.isEmpty()) {
            throw new IllegalStateException("Cannot perform upsert on table '" + tableName + "': no primary key found");
        }

        return items -> {
            logger.info("nodeId={}, Writing {} items to table '{}' via upsert", "DBSink", items.size(), tableName);
            for (Map<String, Object> item : items) {
                upsertRecord(jdbcTemplate, tableName, item, primaryKeys);
            }
        };
    }

    private void upsertRecord(
        NamedParameterJdbcTemplate jdbcTemplate,
        String tableName,
        Map<String, Object> item,
        List<String> primaryKeys
    ) {
        Set<String> allColumns = new LinkedHashSet<>(item.keySet());
        Set<String> nonPkColumns = new LinkedHashSet<>(allColumns);
        nonPkColumns.removeAll(primaryKeys);

        if (nonPkColumns.isEmpty()) {
            return;
        }

        String updateSql = buildUpdateSql(tableName, nonPkColumns, primaryKeys);
        int updateCount = jdbcTemplate.update(updateSql, item);

        if (updateCount == 0) {
            String insertSql = buildInsertSql(tableName, allColumns);
            jdbcTemplate.update(insertSql, item);
        }
    }

    private String buildUpdateSql(String tableName, Set<String> nonPkColumns, List<String> primaryKeys) {
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(tableName).append(" SET ");

        List<String> setClauses = new ArrayList<>();
        for (String column : nonPkColumns) {
            setClauses.add(column + " = :" + column);
        }
        sql.append(String.join(", ", setClauses));

        sql.append(" WHERE ");
        List<String> whereClauses = new ArrayList<>();
        for (String pk : primaryKeys) {
            whereClauses.add(pk + " = :" + pk);
        }
        sql.append(String.join(" AND ", whereClauses));

        return sql.toString();
    }

    private List<String> detectPrimaryKeys(DataSource dataSource, String tableName) {
        List<String> primaryKeys = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    primaryKeys.add(columnName);
                }
            }

            if (primaryKeys.isEmpty()) {
                try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName.toUpperCase())) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        primaryKeys.add(columnName);
                    }
                }
            }

            if (primaryKeys.isEmpty()) {
                try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName.toLowerCase())) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        primaryKeys.add(columnName);
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to detect primary keys for table: " + tableName, e);
        }

        return primaryKeys;
    }

    private void truncateTable(DataSource dataSource, String tableName) {
        try (Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + tableName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to truncate table: " + tableName, e);
        }
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null) {
            throw new IllegalArgumentException("DBSink node requires config");
        }

        if (!config.has("connectionId")) {
            throw new IllegalArgumentException("DBSink requires 'connectionId' in config");
        }

        String connectionId = config.get("connectionId").asText();
        if (connectionId == null || connectionId.trim().isEmpty()) {
            throw new IllegalArgumentException("DBSink 'connectionId' cannot be empty");
        }

        if (!dataSourceProvider.hasDataSource(connectionId)) {
            throw new IllegalArgumentException("DataSource not found for connectionId: " + connectionId);
        }

        if (!config.has("tableName")) {
            throw new IllegalArgumentException("DBSink requires 'tableName' in config");
        }

        String tableName = config.get("tableName").asText();
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("DBSink 'tableName' cannot be empty");
        }

        if (config.has("mode")) {
            String mode = config.get("mode").asText().toLowerCase();
            if (!"insert".equals(mode) && !"upsert".equals(mode) && !"truncate-insert".equals(mode)) {
                throw new IllegalArgumentException("DBSink 'mode' must be 'insert', 'upsert', or 'truncate-insert'");
            }

            if ("upsert".equals(mode)) {
                DataSource dataSource = dataSourceProvider.getOrCreate(connectionId);
                List<String> primaryKeys = detectPrimaryKeys(dataSource, tableName);
                if (primaryKeys.isEmpty()) {
                    throw new IllegalStateException(
                        "Cannot use upsert mode on table '" + tableName + "': no primary key found"
                    );
                }
            }
        }

        if (config.has("batchSize")) {
            int batchSize = config.get("batchSize").asInt();
            if (batchSize <= 0) {
                throw new IllegalArgumentException("DBSink 'batchSize' must be greater than 0");
            }
        }
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return true;
    }
}
