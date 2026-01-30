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
            workflow = detectAndWrapCanvasFormat(payload);
            if (workflow != null) {
                payload.put("workflow", workflow);
            }
        }

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

    @SuppressWarnings("unchecked")
    private static void normalizeNode(Map<String, Object> node) {
        if (node == null) {
            return;
        }

        String nodeType = (String) node.get("type");
        if (nodeType == null || nodeType.isEmpty()) {
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data != null && data.containsKey("nodeType")) {
                nodeType = (String) data.get("nodeType");
                node.put("type", nodeType);
            }
        }

        if (nodeType != null) {
            normalizeNodeType(node, nodeType);
        }

        Map<String, Object> data = (Map<String, Object>) node.get("data");
        if (data != null) {
            normalizeConfigTypes(data);

            Map<String, Object> configObj = (Map<String, Object>) data.get("config");
            if (configObj != null && node.get("config") == null) {
                node.put("config", mapper.convertValue(configObj, JsonNode.class));
            }
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detectAndWrapCanvasFormat(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }

        Object nodesObj = payload.get("nodes");
        Object edgesObj = payload.get("edges");

        if (nodesObj instanceof java.util.List && edgesObj instanceof java.util.List) {
            Map<String, Object> workflow = new java.util.HashMap<>();

            String workflowName = (String) payload.get("workflowName");
            if (workflowName != null && !workflowName.isEmpty()) {
                workflow.put("name", workflowName);
            } else {
                workflow.put("name", "Untitled Workflow");
            }

            String workflowId = (String) payload.get("id");
            if (workflowId != null && !workflowId.isEmpty()) {
                workflow.put("id", workflowId);
            }

            workflow.put("nodes", nodesObj);
            workflow.put("edges", normalizeCanvasEdges((java.util.List<Map<String, Object>>) edgesObj));

            Map<String, Object> wrappedPayload = new java.util.HashMap<>(payload);
            wrappedPayload.put("workflow", workflow);

            return workflow;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, Object>> normalizeCanvasEdges(
            java.util.List<Map<String, Object>> canvasEdges) {
        java.util.List<Map<String, Object>> normalized = new java.util.ArrayList<>();

        for (Map<String, Object> edge : canvasEdges) {
            Map<String, Object> normalizedEdge = new java.util.HashMap<>();

            String source = (String) edge.get("source");
            String target = (String) edge.get("target");

            normalizedEdge.put("source", source);
            normalizedEdge.put("target", target);

            String sourceHandle = (String) edge.get("sourceHandle");
            String targetHandle = (String) edge.get("targetHandle");

            if (sourceHandle != null && !sourceHandle.isEmpty()) {
                normalizedEdge.put("sourceHandle", sourceHandle);
            }

            if (targetHandle != null && !targetHandle.isEmpty()) {
                normalizedEdge.put("targetHandle", targetHandle);
            }

            boolean isControl = false;
            Object isControlObj = edge.get("isControl");

            if (isControlObj instanceof Boolean) {
                isControl = (Boolean) isControlObj;
            } else if (isControlObj instanceof String) {
                isControl = "true".equalsIgnoreCase((String) isControlObj);
            } else {
                String type = (String) edge.get("type");
                isControl = "control".equalsIgnoreCase(type);
            }

            normalizedEdge.put("isControl", isControl);

            normalized.add(normalizedEdge);
        }

        return normalized;
    }
}
