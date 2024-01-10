package com.exemple.cdc.load;

import java.io.File;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoadAgent {

    public static void main(String[] args) {

        var cassandraProcess = ProcessHandle.allProcesses()
                .filter(process -> "cassandra".equals(process.info().user().orElse(null)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no cassandra process is started"));

        ByteBuddyAgent.attach(new File(args[0]), cassandraProcess.pid() + "", args[1]);

        LOG.info("Load CDC agent");

    }
}
