package com.exemple.cdc.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.cassandra.config.DatabaseDescriptor;

import com.exemple.cdc.core.ProcessRun;
import com.exemple.cdc.core.configuration.event.DaggerEventProducerComponent;
import com.exemple.cdc.core.configuration.event.EventProducerModule;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Agent {

    public static void premain(String premainArgs, Instrumentation inst) {
        LOG.info("[Agent] In premain method");
        main(premainArgs, inst);
    }

    public static void agentmain(String agentmainArgs, Instrumentation inst) {
        LOG.info("[Agent] In agentmain method");
        main(agentmainArgs, inst);
    }

    static void main(String mainArgs, Instrumentation inst) {
        DatabaseDescriptor.daemonInitialization();

        assert DatabaseDescriptor.isCDCEnabled() : "cdc_enabled=false in your cassandra configuration, CDC agent not started.";
        assert DatabaseDescriptor.getCDCLogLocation() != null : "cdc_raw_directory=null in your cassandra configuration, CDC agent not started.";

        var agentArgs = new AgentArgs(mainArgs);

        LOG.info("Starting CDC agent");

        var cdcLogPath = Paths.get(DatabaseDescriptor.getCDCLogLocation());
        LOG.info(cdcLogPath.toString());

        var eventProducer = DaggerEventProducerComponent.builder()
                .eventProducerModule(new EventProducerModule(agentArgs.getCdcConfiguration()))
                .build()
                .eventProducer();

        var agentProcess = new ProcessRun(cdcLogPath, eventProducer);
        agentProcess.start();

        LOG.info("CDC agent started");
    }

    @Getter
    private static class AgentArgs {

        private final String cdcConfiguration;

        public AgentArgs(String mainArgs) {

            var agentArgs = Arrays.stream(mainArgs.split(","))
                    .map(arg -> arg.split("="))
                    .collect(Collectors.toMap(arg -> arg[0], arg -> arg[1]));

            cdcConfiguration = agentArgs.get("conf");
        }

    }

}
