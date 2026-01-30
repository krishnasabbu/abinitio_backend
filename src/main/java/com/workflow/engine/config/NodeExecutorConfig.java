package com.workflow.engine.config;

import com.workflow.engine.execution.DBSinkExecutor;
import com.workflow.engine.execution.DBSourceExecutor;
import com.workflow.engine.execution.ErrorSinkExecutor;
import com.workflow.engine.execution.FileSinkExecutor;
import com.workflow.engine.execution.FileSourceExecutor;
import com.workflow.engine.execution.FilterExecutor;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.execution.ReformatExecutor;
import com.workflow.engine.execution.RejectExecutor;
import com.workflow.engine.execution.ValidateExecutor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NodeExecutorConfig {

    @Bean
    public CommandLineRunner registerExecutors(
        NodeExecutorRegistry registry,
        FileSourceExecutor fileSourceExecutor,
        FileSinkExecutor fileSinkExecutor,
        ReformatExecutor reformatExecutor,
        FilterExecutor filterExecutor,
        ValidateExecutor validateExecutor,
        RejectExecutor rejectExecutor,
        ErrorSinkExecutor errorSinkExecutor,
        DBSourceExecutor dbSourceExecutor,
        DBSinkExecutor dbSinkExecutor
    ) {
        return args -> {
            registry.register(fileSourceExecutor);
            registry.register(fileSinkExecutor);
            registry.register(reformatExecutor);
            registry.register(filterExecutor);
            registry.register(validateExecutor);
            registry.register(rejectExecutor);
            registry.register(errorSinkExecutor);
            registry.register(dbSourceExecutor);
            registry.register(dbSinkExecutor);
        };
    }
}
