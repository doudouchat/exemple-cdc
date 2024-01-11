package com.exemple.cdc.core;

import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.mockito.Mockito;

import com.exemple.cdc.core.common.CdcEvent;
import com.exemple.cdc.core.event.EventProducer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentMock {

    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    public static void premain(String agentArgs, Instrumentation inst) {

        var forceSuccess = agentArgs != null ? agentArgs.contains("force_success=true") : false;

        LOG.info("Starting CDC agent mock");
        DatabaseDescriptor.daemonInitialization();
        var cdcLogPath = Paths.get(DatabaseDescriptor.getCDCLogLocation());
        LOG.info(cdcLogPath.toString());

        var eventProducer = Mockito.mock(EventProducer.class);
        Mockito.doAnswer(invocation -> {
            CdcEvent event = invocation.getArgument(0);

            if ("FAILURE_EVENT".equals(event.getEventType())) {

                if (forceSuccess) {
                    LOG.debug("SUCCESS EVENT " + event.getDate());
                } else {
                    LOG.error("FAILURE EVENT " + event.getDate());
                    throw new Exception("unexpected exception");
                }
            }

            if ("SUCCESS_EVENT".equals(event.getEventType())) {
                LOG.debug("SUCCESS EVENT " + event.getDate());
            }

            return null;
        }).when(eventProducer).send(Mockito.any());

        var agentProcess = new ProcessRun(cdcLogPath, eventProducer);
        agentProcess.start();

        LOG.info("CDC agent mock started");
    }
}
