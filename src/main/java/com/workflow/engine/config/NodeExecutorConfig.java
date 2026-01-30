package com.workflow.engine.config;

import com.workflow.engine.execution.AggregateExecutor;
import com.workflow.engine.execution.AlertExecutor;
import com.workflow.engine.execution.AssertExecutor;
import com.workflow.engine.execution.AuditExecutor;
import com.workflow.engine.execution.BroadcastExecutor;
import com.workflow.engine.execution.CheckpointExecutor;
import com.workflow.engine.execution.CollectExecutor;
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
import com.workflow.engine.execution.HashPartitionExecutor;
import com.workflow.engine.execution.IntersectExecutor;
import com.workflow.engine.execution.JoinExecutor;
import com.workflow.engine.execution.LimitExecutor;
import com.workflow.engine.execution.LookupExecutor;
import com.workflow.engine.execution.MapExecutor;
import com.workflow.engine.execution.MergeExecutor;
import com.workflow.engine.execution.MinusExecutor;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.execution.NormalizeExecutor;
import com.workflow.engine.execution.PartitionExecutor;
import com.workflow.engine.execution.RangePartitionExecutor;
import com.workflow.engine.execution.ReformatExecutor;
import com.workflow.engine.execution.RejectExecutor;
import com.workflow.engine.execution.ReplicateExecutor;
import com.workflow.engine.execution.ResumeExecutor;
import com.workflow.engine.execution.SampleExecutor;
import com.workflow.engine.execution.SchemaValidatorExecutor;
import com.workflow.engine.execution.SLAExecutor;
import com.workflow.engine.execution.SortExecutor;
import com.workflow.engine.execution.StartExecutor;
import com.workflow.engine.execution.SwitchExecutor;
import com.workflow.engine.execution.ThrottleExecutor;
import com.workflow.engine.execution.ValidateExecutor;
import com.workflow.engine.execution.WaitExecutor;
import com.workflow.engine.execution.JobConditionExecutor;
import com.workflow.engine.execution.SplitExecutor;
import com.workflow.engine.execution.GatherExecutor;
import com.workflow.engine.execution.KafkaSourceExecutor;
import com.workflow.engine.execution.KafkaSinkExecutor;
import com.workflow.engine.execution.RestAPISourceExecutor;
import com.workflow.engine.execution.RestAPISinkExecutor;
import com.workflow.engine.execution.DBExecuteExecutor;
import com.workflow.engine.execution.XMLParseExecutor;
import com.workflow.engine.execution.XMLValidateExecutor;
import com.workflow.engine.execution.JSONFlattenExecutor;
import com.workflow.engine.execution.JSONExplodeExecutor;
import com.workflow.engine.execution.RollupExecutor;
import com.workflow.engine.execution.WindowExecutor;
import com.workflow.engine.execution.ScanExecutor;
import com.workflow.engine.execution.EncryptExecutor;
import com.workflow.engine.execution.DecryptExecutor;
import com.workflow.engine.execution.PythonNodeExecutor;
import com.workflow.engine.execution.ScriptNodeExecutor;
import com.workflow.engine.execution.ShellNodeExecutor;
import com.workflow.engine.execution.CustomNodeExecutor;
import com.workflow.engine.execution.SubgraphExecutor;
import com.workflow.engine.execution.WebServiceCallExecutor;
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
        MinusExecutor minusExecutor,
        PartitionExecutor partitionExecutor,
        HashPartitionExecutor hashPartitionExecutor,
        RangePartitionExecutor rangePartitionExecutor,
        ReplicateExecutor replicateExecutor,
        BroadcastExecutor broadcastExecutor,
        CollectExecutor collectExecutor,
        AlertExecutor alertExecutor,
        AuditExecutor auditExecutor,
        CheckpointExecutor checkpointExecutor,
        ResumeExecutor resumeExecutor,
        SLAExecutor slaExecutor,
        ThrottleExecutor throttleExecutor,
        WaitExecutor waitExecutor,
        JobConditionExecutor jobConditionExecutor,
        SplitExecutor splitExecutor,
        GatherExecutor gatherExecutor,
        KafkaSourceExecutor kafkaSourceExecutor,
        KafkaSinkExecutor kafkaSinkExecutor,
        RestAPISourceExecutor restAPISourceExecutor,
        RestAPISinkExecutor restAPISinkExecutor,
        DBExecuteExecutor dbExecuteExecutor,
        XMLParseExecutor xmlParseExecutor,
        XMLValidateExecutor xmlValidateExecutor,
        JSONFlattenExecutor jsonFlattenExecutor,
        JSONExplodeExecutor jsonExplodeExecutor,
        RollupExecutor rollupExecutor,
        WindowExecutor windowExecutor,
        ScanExecutor scanExecutor,
        EncryptExecutor encryptExecutor,
        DecryptExecutor decryptExecutor,
        PythonNodeExecutor pythonNodeExecutor,
        ScriptNodeExecutor scriptNodeExecutor,
        ShellNodeExecutor shellNodeExecutor,
        CustomNodeExecutor customNodeExecutor,
        SubgraphExecutor subgraphExecutor,
        WebServiceCallExecutor webServiceCallExecutor
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
            registry.register(partitionExecutor);
            registry.register(hashPartitionExecutor);
            registry.register(rangePartitionExecutor);
            registry.register(replicateExecutor);
            registry.register(broadcastExecutor);
            registry.register(collectExecutor);
            registry.register(alertExecutor);
            registry.register(auditExecutor);
            registry.register(checkpointExecutor);
            registry.register(resumeExecutor);
            registry.register(slaExecutor);
            registry.register(throttleExecutor);
            registry.register(waitExecutor);
            registry.register(jobConditionExecutor);
            registry.register(splitExecutor);
            registry.register(gatherExecutor);
            registry.register(kafkaSourceExecutor);
            registry.register(kafkaSinkExecutor);
            registry.register(restAPISourceExecutor);
            registry.register(restAPISinkExecutor);
            registry.register(dbExecuteExecutor);
            registry.register(xmlParseExecutor);
            registry.register(xmlValidateExecutor);
            registry.register(jsonFlattenExecutor);
            registry.register(jsonExplodeExecutor);
            registry.register(rollupExecutor);
            registry.register(windowExecutor);
            registry.register(scanExecutor);
            registry.register(encryptExecutor);
            registry.register(decryptExecutor);
            registry.register(pythonNodeExecutor);
            registry.register(scriptNodeExecutor);
            registry.register(shellNodeExecutor);
            registry.register(customNodeExecutor);
            registry.register(subgraphExecutor);
            registry.register(webServiceCallExecutor);
        };
    }
}
