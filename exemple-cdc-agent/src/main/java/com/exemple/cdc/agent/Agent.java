package com.exemple.cdc.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.service.StorageService;

import com.exemple.cdc.agent.commitlog.CommitLogProcess;
import com.exemple.cdc.agent.common.DirectoryWatcher;
import com.exemple.cdc.agent.core.commitlog.CommitLogModule;
import com.exemple.cdc.agent.core.commitlog.DaggerCommitLogComponent;
import com.exemple.cdc.agent.core.event.DaggerEventProducerComponent;
import com.exemple.cdc.agent.core.event.EventProducerModule;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Agent {

    public static void premain(String agentArgs, Instrumentation inst) {
        LOG.info("[Agent] In premain method");
        main(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        LOG.info("[Agent] In agentmain method");
        main(agentArgs, inst);
    }

    static void main(String agentArgs, Instrumentation inst) {
        DatabaseDescriptor.daemonInitialization();
        if (!DatabaseDescriptor.isCDCEnabled()) {
            LOG.error("cdc_enabled=false in your cassandra configuration, CDC agent not started.");
        } else if (DatabaseDescriptor.getCDCLogLocation() == null) {
            LOG.error("cdc_raw_directory=null in your cassandra configuration, CDC agent not started.");
        } else {
            startCdcAgent(agentArgs);
        }
    }

    @SneakyThrows
    private static void startCdcAgent(String agentArgs) {
        LOG.info("Starting CDC agent");

        var cdcLogPath = Paths.get(DatabaseDescriptor.getCDCLogLocation());
        LOG.info(cdcLogPath.toString());

        var commitLogExecutor = Executors.newSingleThreadExecutor();
        commitLogExecutor.submit(() -> process(cdcLogPath));

        LOG.info("CDC agent started");

    }

    @SneakyThrows
    private static void process(Path cdcLogPath) {

        do {
            Thread.sleep(1_000);
        } while (StorageService.instance.getLocalHostUUID() == null);

        var component = DaggerCommitLogComponent.builder().commitLogModule(new CommitLogModule(
                DaggerEventProducerComponent.builder().eventProducerModule(new EventProducerModule("/tmp/conf/exemple-cdc.yml")).build()
                        .eventProducer()))
                .build();

        DirectoryWatcher.onChangeOrCreateFile(cdcLogPath, 0, file -> file.getAbsolutePath().endsWith("_cdc.idx"),
                file -> new CommitLogProcess(file, component.commitLogReader(), component.commitLogReadHandler()).process());
    }

}
