package com.exemple.cdc.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;

import org.apache.cassandra.config.DatabaseDescriptor;

import com.exemple.cdc.agent.core.event.DaggerEventProducerComponent;
import com.exemple.cdc.agent.core.event.EventProducerModule;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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

            LOG.info("Starting CDC agent");

            var cdcLogPath = Paths.get(DatabaseDescriptor.getCDCLogLocation());
            LOG.info(cdcLogPath.toString());

            var eventProducer = DaggerEventProducerComponent.builder().eventProducerModule(new EventProducerModule("/tmp/conf/exemple-cdc.yml"))
                    .build()
                    .eventProducer();

            var agentProcess = new AgentProcess(cdcLogPath, eventProducer);
            agentProcess.start();

            LOG.info("CDC agent started");

        }
    }

}
