package com.workflow.engine.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

public class PayloadNormalizer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<String, Object> normalize(Map<String, Object> payload) {
        if (payload == null) {
            return payload;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> workflow = (Map<String, Object>) payload.get("workflow");
        if (workflow == null) {
            return payload;
        }

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> nodes = (java.util.List<Map<String, Object>>) workflow.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                normalizeNode(node);
            }
        }

        return payload;
    }

    private static void normalizeNode(Map<String, Object> node) {
        if (node == null) {
            return;
        }

        String nodeType = (String) node.get("type");
        if (nodeType == null || nodeType.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data != null && data.containsKey("nodeType")) {
                nodeType = (String) data.get("nodeType");
                node.put("type", nodeType);
            }
        }

        if (nodeType != null) {
            normalizeNodeType(node, nodeType);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) node.get("data");
        if (data != null) {
            normalizeConfigTypes(data);
        }
    }

    private static void normalizeNodeType(Map<String, Object> node, String nodeType) {
        node.put("type", nodeType);
    }

    private static void normalizeConfigTypes(Map<String, Object> config) {
        if (config == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                String strValue = (String) value;

                if ("array".equals(key) || key.endsWith("Fields") || key.endsWith("Keys") || key.endsWith("Params")) {
                    if (strValue.contains(",") && !strValue.isEmpty()) {
                        String[] parts = strValue.split(",");
                        java.util.List<String> list = new java.util.ArrayList<>();
                        for (String part : parts) {
                            list.add(part.trim());
                        }
                        config.put(key, list);
                    }
                }
            }
        }
    }

    public static JsonNode normalizeJsonNode(JsonNode node) {
        if (!node.isObject()) {
            return node;
        }

        ObjectNode normalized = mapper.createObjectNode();

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            if ("data".equals(fieldName) && fieldValue.isObject()) {
                ObjectNode data = (ObjectNode) fieldValue;
                if (data.has("nodeType") && !node.has("type")) {
                    normalized.put("type", data.get("nodeType").asText());
                }
            }

            normalized.set(fieldName, normalizeJsonNode(fieldValue));
        }

        return normalized;
    }
}
