package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class XMLValidateExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(XMLValidateExecutor.class);

    @Override
    public String getNodeType() {
        return "XMLValidate";
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
        return new ListItemReader<>(items != null ? items : new ArrayList<>());
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String xmlField = config.has("xmlField") ? config.get("xmlField").asText() : "xml";
        String requiredFieldsStr = config.has("requiredFields") ? config.get("requiredFields").asText() : "";

        List<String> requiredFields = new ArrayList<>();
        if (!requiredFieldsStr.trim().isEmpty()) {
            for (String f : requiredFieldsStr.split(",")) { requiredFields.add(f.trim()); }
        }

        return item -> {
            Map<String, Object> result = new LinkedHashMap<>(item);
            List<String> errors = new ArrayList<>();

            Object xmlValue = item.get(xmlField);
            if (xmlValue instanceof String) {
                String xmlString = (String) xmlValue;
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
                    doc.getDocumentElement().normalize();
                } catch (Exception e) {
                    errors.add("XML parse error: " + e.getMessage());
                }
            }

            for (String field : requiredFields) {
                if (!item.containsKey(field) || item.get(field) == null) {
                    errors.add("Missing required field: " + field);
                }
            }

            if (!errors.isEmpty()) {
                result.put("_validationErrors", errors);
                result.put("_valid", false);
            } else {
                result.put("_valid", true);
            }

            return result;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            List<Map<String, Object>> validList = new ArrayList<>();
            List<Map<String, Object>> invalidList = new ArrayList<>();

            for (Map<String, Object> item : items) {
                if (item == null) continue;
                Object valid = item.get("_valid");
                if (Boolean.FALSE.equals(valid)) {
                    invalidList.add(item);
                } else {
                    validList.add(item);
                }
            }

            logger.info("nodeId={}, XMLValidate: {} valid, {} invalid",
                context.getNodeDefinition().getId(), validList.size(), invalidList.size());
            context.setVariable("outputItems", validList);
            context.setVariable("invalidItems", invalidList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("nodeType=XMLValidate, nodeId=" + context.getNodeDefinition().getId()
                + ", missing config object");
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
