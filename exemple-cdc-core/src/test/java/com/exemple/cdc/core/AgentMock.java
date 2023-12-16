package com.exemple.cdc.core;

import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.mockito.Mockito;

import com.exemple.cdc.core.AgentProcess;
import com.exemple.cdc.core.event.EventProducer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentMock {

    public static void premain(String agentArgs, Instrumentation inst) {

        LOG.info("Starting CDC agent mock");
        DatabaseDescriptor.daemonInitialization();
        var cdcLogPath = Paths.get(DatabaseDescriptor.getCDCLogLocation());
        LOG.info(cdcLogPath.toString());

        var eventProducer = Mockito.mock(EventProducer.class);

        var agentProcess = new AgentProcess(cdcLogPath, eventProducer);
        agentProcess.start();

        LOG.info("CDC agent mock started");
    }
}
