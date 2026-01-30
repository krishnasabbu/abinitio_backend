package com.workflow.engine.execution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaParser {

    private final List<String> fieldNames;
    private final Map<String, String> fieldTypes;

    public SchemaParser(String schema) {
        this.fieldNames = new ArrayList<>();
        this.fieldTypes = new HashMap<>();

        if (schema != null && !schema.trim().isEmpty()) {
            parseSchema(schema);
        }
    }

    private void parseSchema(String schema) {
        String[] fields = schema.split(",");

        for (String field : fields) {
            field = field.trim();
            if (field.isEmpty()) {
                continue;
            }

            if (field.contains(":")) {
                String[] parts = field.split(":", 2);
                String name = parts[0].trim();
                String type = parts[1].trim().toLowerCase();
                fieldNames.add(name);
                fieldTypes.put(name, type);
            } else {
                fieldNames.add(field);
                fieldTypes.put(field, "string");
            }
        }
    }

    public List<String> getFieldNames() {
        return fieldNames;
    }

    public boolean hasFieldTypes() {
        return !fieldTypes.isEmpty();
    }

    public String getFieldType(String fieldName) {
        return fieldTypes.getOrDefault(fieldName, "string");
    }

    public Object convertValue(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        String type = getFieldType(fieldName);

        try {
            switch (type) {
                case "int":
                case "integer":
                    return Integer.parseInt(value.trim());

                case "long":
                    return Long.parseLong(value.trim());

                case "double":
                    return Double.parseDouble(value.trim());

                case "decimal":
                    return new BigDecimal(value.trim());

                case "bool":
                case "boolean":
                    return Boolean.parseBoolean(value.trim());

                case "date":
                    return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);

                case "string":
                default:
                    return value;
            }
        } catch (Exception e) {
            return value;
        }
    }

    public boolean isEmpty() {
        return fieldNames.isEmpty();
    }

    public int getFieldCount() {
        return fieldNames.size();
    }
}
