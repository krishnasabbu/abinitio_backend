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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class XMLParseExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(XMLParseExecutor.class);

    @Override
    public String getNodeType() {
        return "XMLParse";
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

        return item -> {
            Object xmlValue = item.get(xmlField);
            if (xmlValue == null || !(xmlValue instanceof String)) {
                return item;
            }

            String xmlString = (String) xmlValue;
            Map<String, Object> parsed = parseXml(xmlString);

            Map<String, Object> result = new LinkedHashMap<>(item);
            result.putAll(parsed);
            return result;
        };
    }

    private Map<String, Object> parseXml(String xmlString) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
            Element root = doc.getDocumentElement();
            result.put("_rootElement", root.getTagName());
            parseElement(root, result);
        } catch (Exception e) {
            logger.warn("Failed to parse XML: {}", e.getMessage());
            result.put("_parseError", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void parseElement(Element element, Map<String, Object> target) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();

                if (hasChildElements(childElement)) {
                    Map<String, Object> childMap = new LinkedHashMap<>();
                    parseElement(childElement, childMap);
                    Object existing = target.get(tagName);
                    if (existing instanceof List) {
                        ((List<Object>) existing).add(childMap);
                    } else if (existing instanceof Map) {
                        List<Object> list = new ArrayList<>();
                        list.add(existing);
                        list.add(childMap);
                        target.put(tagName, list);
                    } else {
                        target.put(tagName, childMap);
                    }
                } else {
                    String text = childElement.getTextContent().trim();
                    Object existing = target.get(tagName);
                    if (existing instanceof List) {
                        ((List<Object>) existing).add(text);
                    } else if (existing != null) {
                        List<Object> list = new ArrayList<>();
                        list.add(existing);
                        list.add(text);
                        target.put(tagName, list);
                    } else {
                        target.put(tagName, text);
                    }
                }
            }
        }
    }

    private boolean hasChildElements(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("nodeId={}, XMLParse wrote {} items", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("nodeType=XMLParse, nodeId=" + context.getNodeDefinition().getId()
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
