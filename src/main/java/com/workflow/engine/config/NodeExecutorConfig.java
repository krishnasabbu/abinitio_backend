package com.workflow.engine.config;

import com.workflow.engine.execution.FileSourceExecutor;
import com.workflow.engine.execution.NodeExecutorRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NodeExecutorConfig {

    @Bean
    public CommandLineRunner registerExecutors(
        NodeExecutorRegistry registry,
        FileSourceExecutor fileSourceExecutor
    ) {
        return args -> {
            registry.register(fileSourceExecutor);
        };
    }
}
