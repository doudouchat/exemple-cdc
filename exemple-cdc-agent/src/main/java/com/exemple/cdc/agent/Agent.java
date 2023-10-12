package com.exemple.cdc.agent;

import java.lang.instrument.Instrumentation;

import org.apache.cassandra.config.DatabaseDescriptor;

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
            startCdcAgent(agentArgs);
        }
    }

    static void startCdcAgent(String agentArgs) {
        LOG.info("Starting CDC agent, cdc_raw_directory={}", DatabaseDescriptor.getCDCLogLocation());

        LOG.info("CDC agent started");
    }
}
