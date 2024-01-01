package com.exemple.cdc.core;

import java.io.File;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;

@Slf4j
public class ReloadAgent {

    public static void main(String[] args) {

        var cassandraProcess = ProcessHandle.allProcesses()
                .filter(process -> "cassandra".equals(process.info().user().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no cassandra process is started"));

        ByteBuddyAgent.attach(new File("/exemple-cdc-agent.jar"), cassandraProcess.pid() + "");

        LOG.info("Load CDC agent");

    }
}
