package com.workflow.engine.config;

import com.workflow.engine.graph.SubgraphExpander;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SubgraphConfig {

    @Value("${workflow.subgraph.maxExpansionDepth:10}")
    private int maxExpansionDepth;

    @Bean
    public SubgraphExpander subgraphExpander() {
        return new SubgraphExpander(new java.util.HashMap<>(), maxExpansionDepth);
    }
}
