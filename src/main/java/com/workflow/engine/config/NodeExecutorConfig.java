package com.workflow.engine.config;

import com.workflow.engine.execution.AggregateExecutor;
import com.workflow.engine.execution.AssertExecutor;
import com.workflow.engine.execution.ComputeExecutor;
import com.workflow.engine.execution.CountExecutor;
import com.workflow.engine.execution.DBSinkExecutor;
import com.workflow.engine.execution.DBSourceExecutor;
import com.workflow.engine.execution.DecisionExecutor;
import com.workflow.engine.execution.DeduplicateExecutor;
import com.workflow.engine.execution.DenormalizeExecutor;
import com.workflow.engine.execution.EndExecutor;
import com.workflow.engine.execution.ErrorSinkExecutor;
import com.workflow.engine.execution.FailJobExecutor;
import com.workflow.engine.execution.FileSinkExecutor;
import com.workflow.engine.execution.FileSourceExecutor;
import com.workflow.engine.execution.FilterExecutor;
import com.workflow.engine.execution.IntersectExecutor;
import com.workflow.engine.execution.JoinExecutor;
import com.workflow.engine.execution.LimitExecutor;
import com.workflow.engine.execution.LookupExecutor;
import com.workflow.engine.execution.MapExecutor;
import com.workflow.engine.execution.MergeExecutor;
import com.workflow.engine.execution.MinusExecutor;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.execution.NormalizeExecutor;
import com.workflow.engine.execution.ReformatExecutor;
import com.workflow.engine.execution.RejectExecutor;
import com.workflow.engine.execution.SampleExecutor;
import com.workflow.engine.execution.SchemaValidatorExecutor;
import com.workflow.engine.execution.SortExecutor;
import com.workflow.engine.execution.StartExecutor;
import com.workflow.engine.execution.SwitchExecutor;
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
        DBSinkExecutor dbSinkExecutor,
        ComputeExecutor computeExecutor,
        MapExecutor mapExecutor,
        NormalizeExecutor normalizeExecutor,
        DenormalizeExecutor denormalizeExecutor,
        SampleExecutor sampleExecutor,
        LimitExecutor limitExecutor,
        CountExecutor countExecutor,
        AssertExecutor assertExecutor,
        StartExecutor startExecutor,
        EndExecutor endExecutor,
        FailJobExecutor failJobExecutor,
        DecisionExecutor decisionExecutor,
        SwitchExecutor switchExecutor,
        SortExecutor sortExecutor,
        AggregateExecutor aggregateExecutor,
        SchemaValidatorExecutor schemaValidatorExecutor,
        JoinExecutor joinExecutor,
        LookupExecutor lookupExecutor,
        MergeExecutor mergeExecutor,
        DeduplicateExecutor deduplicateExecutor,
        IntersectExecutor intersectExecutor,
        MinusExecutor minusExecutor
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
            registry.register(computeExecutor);
            registry.register(mapExecutor);
            registry.register(normalizeExecutor);
            registry.register(denormalizeExecutor);
            registry.register(sampleExecutor);
            registry.register(limitExecutor);
            registry.register(countExecutor);
            registry.register(assertExecutor);
            registry.register(startExecutor);
            registry.register(endExecutor);
            registry.register(failJobExecutor);
            registry.register(decisionExecutor);
            registry.register(switchExecutor);
            registry.register(sortExecutor);
            registry.register(aggregateExecutor);
            registry.register(schemaValidatorExecutor);
            registry.register(joinExecutor);
            registry.register(lookupExecutor);
            registry.register(mergeExecutor);
            registry.register(deduplicateExecutor);
            registry.register(intersectExecutor);
            registry.register(minusExecutor);
        };
    }
}
