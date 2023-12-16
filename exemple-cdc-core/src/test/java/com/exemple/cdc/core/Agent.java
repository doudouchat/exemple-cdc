package com.exemple.cdc.core;

import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;

import org.apache.cassandra.config.DatabaseDescriptor;

import com.exemple.cdc.core.configuration.event.DaggerEventProducerComponent;
import com.exemple.cdc.core.configuration.event.EventProducerModule;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Agent {

    public static void premain(String agentArgs, Instrumentation inst) {

        LOG.info("Starting CDC agent");
        DatabaseDescriptor.daemonInitialization();
        var cdcLogPath = Paths.get(DatabaseDescriptor.getCDCLogLocation());
        LOG.info(cdcLogPath.toString());

        var eventProducer = DaggerEventProducerComponent.builder().eventProducerModule(new EventProducerModule("/tmp/conf/exemple-cdc.yml"))
                .build()
                .eventProducer();

        var agentProcess = new ProcessRun(cdcLogPath, eventProducer);
        agentProcess.start();

        LOG.info("CDC agent started");
    }
}
