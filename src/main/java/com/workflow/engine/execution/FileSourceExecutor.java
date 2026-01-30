package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for reading data from local filesystem files.
 *
 * Reads records from CSV, TXT, or other delimited/fixed-width text files.
 * Supports multiple formats including:
 * - CSV files with configurable delimiter and headers
 * - Fixed-width text files with column ranges
 * - Delimited text files with custom delimiters
 *
 * Configuration:
 * - filePath: (required) Path to the input file
 * - fileType: (optional) File type - "csv" or "txt" (default: "csv")
 * - encoding: (optional) Character encoding (default: "UTF-8")
 * - delimiter: (optional for CSV) Field delimiter (default: ",")
 * - header: (optional for CSV) Whether first line is header (default: true)
 * - schema: (optional) Schema definition for type conversion
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class FileSourceExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(FileSourceExecutor.class);

    @Override
    public String getNodeType() {
        return "FileSource";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        logger.debug("nodeId={}, Creating file source reader", context.getNodeDefinition().getId());
        JsonNode config = context.getNodeDefinition().getConfig();

        String filePath = config.get("filePath").asText();
        String fileType = config.has("fileType") ? config.get("fileType").asText().toLowerCase() : "csv";
        String encoding = config.has("encoding") ? config.get("encoding").asText() : "UTF-8";

        logger.debug("nodeId={}, Reading from file: {} (type: {}, encoding: {})",
            context.getNodeDefinition().getId(), filePath, fileType, encoding);

        if ("csv".equals(fileType)) {
            return createCsvReader(config, filePath, encoding);
        } else if ("txt".equals(fileType)) {
            return createTxtReader(config, filePath, encoding);
        } else if ("json".equals(fileType) || "excel".equals(fileType) || "parquet".equals(fileType)) {
            logger.error("nodeId={}, File type not implemented: {}", context.getNodeDefinition().getId(), fileType);
            throw new UnsupportedOperationException(
                "File type '" + fileType + "' is not implemented yet."
            );
        } else {
            logger.error("nodeId={}, File type not supported: {}", context.getNodeDefinition().getId(), fileType);
            throw new UnsupportedOperationException(
                "File type '" + fileType + "' is not supported."
            );
        }
    }

    private ItemReader<Map<String, Object>> createCsvReader(JsonNode config, String filePath, String encoding) {
        String delimiter = config.has("delimiter") ? config.get("delimiter").asText() : ",";
        boolean hasHeader = config.has("header") ? config.get("header").asBoolean() : true;
        String schemaStr = config.has("schema") ? config.get("schema").asText() : null;

        SchemaParser schema = new SchemaParser(schemaStr);

        FlatFileItemReader<Map<String, Object>> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(filePath));
        reader.setEncoding(encoding);
        reader.setLinesToSkip(hasHeader ? 1 : 0);

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter(delimiter);

        DefaultLineMapper<Map<String, Object>> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);

        if (!schema.isEmpty()) {
            tokenizer.setNames(schema.getFieldNames().toArray(new String[0]));
        } else if (hasHeader) {
            reader.setSkippedLinesCallback(line -> {
                String[] headerTokens = line.split(delimiter, -1);
                tokenizer.setNames(headerTokens);
            });
        }

        lineMapper.setFieldSetMapper(fieldSet -> {
            Map<String, Object> map = new HashMap<>();
            String[] fieldNames = fieldSet.getNames();

            if (fieldNames == null || fieldNames.length == 0) {
                int fieldCount = fieldSet.getFieldCount();
                for (int i = 0; i < fieldCount; i++) {
                    map.put("col" + (i + 1), fieldSet.readString(i));
                }
            } else {
                for (String fieldName : fieldNames) {
                    String value = fieldSet.readString(fieldName);
                    if (!schema.isEmpty()) {
                        map.put(fieldName, schema.convertValue(fieldName, value));
                    } else {
                        map.put(fieldName, value);
                    }
                }
            }

            return map;
        });

        reader.setLineMapper(lineMapper);
        return reader;
    }

    private ItemReader<Map<String, Object>> createTxtReader(JsonNode config, String filePath, String encoding) {
        String lineFormat = config.has("lineFormat") ? config.get("lineFormat").asText().toLowerCase() : "raw";

        switch (lineFormat) {
            case "raw":
                return createRawTxtReader(config, filePath, encoding);
            case "delimited":
                return createDelimitedTxtReader(config, filePath, encoding);
            case "fixedwidth":
                return createFixedWidthTxtReader(config, filePath, encoding);
            default:
                throw new IllegalArgumentException("Unsupported lineFormat: " + lineFormat);
        }
    }

    private ItemReader<Map<String, Object>> createRawTxtReader(JsonNode config, String filePath, String encoding) {
        String schemaStr = config.has("schema") ? config.get("schema").asText() : null;
        SchemaParser schema = new SchemaParser(schemaStr);

        String fieldName = "line";
        if (!schema.isEmpty() && schema.getFieldCount() == 1) {
            fieldName = schema.getFieldNames().get(0);
        }

        FlatFileItemReader<Map<String, Object>> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(filePath));
        reader.setEncoding(encoding);

        final String finalFieldName = fieldName;
        reader.setLineMapper((line, lineNumber) -> {
            Map<String, Object> map = new HashMap<>();
            map.put(finalFieldName, line);
            return map;
        });

        return reader;
    }

    private ItemReader<Map<String, Object>> createDelimitedTxtReader(JsonNode config, String filePath, String encoding) {
        String delimiter = config.has("delimiter") ? config.get("delimiter").asText() : "|";
        boolean hasHeader = config.has("header") ? config.get("header").asBoolean() : true;
        String schemaStr = config.has("schema") ? config.get("schema").asText() : null;

        SchemaParser schema = new SchemaParser(schemaStr);

        FlatFileItemReader<Map<String, Object>> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(filePath));
        reader.setEncoding(encoding);
        reader.setLinesToSkip(hasHeader ? 1 : 0);

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter(delimiter);

        DefaultLineMapper<Map<String, Object>> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);

        if (!schema.isEmpty()) {
            tokenizer.setNames(schema.getFieldNames().toArray(new String[0]));
        } else if (hasHeader) {
            reader.setSkippedLinesCallback(line -> {
                String[] headerTokens = line.split(delimiter, -1);
                tokenizer.setNames(headerTokens);
            });
        }

        lineMapper.setFieldSetMapper(fieldSet -> {
            Map<String, Object> map = new HashMap<>();
            String[] fieldNames = fieldSet.getNames();

            if (fieldNames == null || fieldNames.length == 0) {
                int fieldCount = fieldSet.getFieldCount();
                for (int i = 0; i < fieldCount; i++) {
                    map.put("col" + (i + 1), fieldSet.readString(i));
                }
            } else {
                for (int i = 0; i < fieldNames.length && i < fieldSet.getFieldCount(); i++) {
                    String fieldName = fieldNames[i];
                    String value = fieldSet.readString(i);
                    if (!schema.isEmpty()) {
                        map.put(fieldName, schema.convertValue(fieldName, value));
                    } else {
                        map.put(fieldName, value);
                    }
                }

                for (int i = fieldNames.length; i < fieldSet.getFieldCount(); i++) {
                    map.put("extra_" + (i - fieldNames.length + 1), fieldSet.readString(i));
                }
            }

            return map;
        });

        reader.setLineMapper(lineMapper);
        return reader;
    }

    private ItemReader<Map<String, Object>> createFixedWidthTxtReader(JsonNode config, String filePath, String encoding) {
        String fieldWidthsStr = config.has("fieldWidths") ? config.get("fieldWidths").asText() : null;
        boolean hasHeader = config.has("header") ? config.get("header").asBoolean() : false;
        String schemaStr = config.has("schema") ? config.get("schema").asText() : null;

        if (fieldWidthsStr == null || fieldWidthsStr.trim().isEmpty()) {
            throw new IllegalArgumentException("fieldWidths is required for fixedWidth format");
        }

        List<Integer> widths = parseFieldWidths(fieldWidthsStr);
        SchemaParser schema = new SchemaParser(schemaStr);

        FlatFileItemReader<Map<String, Object>> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(filePath));
        reader.setEncoding(encoding);
        reader.setLinesToSkip(hasHeader ? 1 : 0);

        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
        Range[] ranges = createRanges(widths);
        tokenizer.setColumns(ranges);

        String[] fieldNames;
        if (!schema.isEmpty()) {
            fieldNames = schema.getFieldNames().toArray(new String[0]);
        } else {
            fieldNames = new String[widths.size()];
            for (int i = 0; i < widths.size(); i++) {
                fieldNames[i] = "col" + (i + 1);
            }
        }
        tokenizer.setNames(fieldNames);

        DefaultLineMapper<Map<String, Object>> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);

        lineMapper.setFieldSetMapper(fieldSet -> {
            Map<String, Object> map = new HashMap<>();
            for (String fieldName : fieldNames) {
                String value = fieldSet.readString(fieldName).trim();
                if (!schema.isEmpty()) {
                    map.put(fieldName, schema.convertValue(fieldName, value));
                } else {
                    map.put(fieldName, value);
                }
            }
            return map;
        });

        reader.setLineMapper(lineMapper);
        return reader;
    }

    private List<Integer> parseFieldWidths(String fieldWidthsStr) {
        List<Integer> widths = new ArrayList<>();
        String[] parts = fieldWidthsStr.split(",");
        for (String part : parts) {
            widths.add(Integer.parseInt(part.trim()));
        }
        return widths;
    }

    private Range[] createRanges(List<Integer> widths) {
        Range[] ranges = new Range[widths.size()];
        int start = 1;
        for (int i = 0; i < widths.size(); i++) {
            int end = start + widths.get(i) - 1;
            ranges[i] = new Range(start, end);
            start = end + 1;
        }
        return ranges;
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {};
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("filePath")) {
            throw new IllegalArgumentException("FileSource node requires 'filePath' in config");
        }

        String filePath = config.get("filePath").asText();

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("FileSource 'filePath' cannot be empty");
        }

        if (!Files.exists(Paths.get(filePath))) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        if (!Files.isRegularFile(Paths.get(filePath))) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
        }

        String fileType = config.has("fileType") ? config.get("fileType").asText().toLowerCase() : "csv";

        if ("txt".equals(fileType)) {
            String lineFormat = config.has("lineFormat") ? config.get("lineFormat").asText().toLowerCase() : "raw";

            if ("fixedwidth".equals(lineFormat)) {
                String fieldWidthsStr = config.has("fieldWidths") ? config.get("fieldWidths").asText() : null;
                if (fieldWidthsStr == null || fieldWidthsStr.trim().isEmpty()) {
                    throw new IllegalArgumentException("fieldWidths is required for fixedWidth format");
                }

                try {
                    List<Integer> widths = parseFieldWidths(fieldWidthsStr);
                    if (widths.isEmpty()) {
                        throw new IllegalArgumentException("fieldWidths must contain at least one width");
                    }

                    String schemaStr = config.has("schema") ? config.get("schema").asText() : null;
                    if (schemaStr != null && !schemaStr.trim().isEmpty()) {
                        SchemaParser schema = new SchemaParser(schemaStr);
                        if (!schema.isEmpty() && schema.getFieldCount() != widths.size()) {
                            throw new IllegalArgumentException(
                                "Schema field count (" + schema.getFieldCount() +
                                ") must match fieldWidths count (" + widths.size() + ")"
                            );
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid fieldWidths format: " + e.getMessage());
                }
            }
        }

        String delimiter = config.has("delimiter") ? config.get("delimiter").asText() :
                           ("csv".equals(fileType) ? "," : "|");
        if (delimiter.length() != 1) {
            throw new IllegalArgumentException("delimiter must be a single character");
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
