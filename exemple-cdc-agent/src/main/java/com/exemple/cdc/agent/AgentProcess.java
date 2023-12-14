package com.exemple.cdc.agent;

import java.nio.file.Path;
import java.util.concurrent.Executors;

import org.apache.cassandra.db.commitlog.CommitLogReadHandler;
import org.apache.cassandra.db.commitlog.CommitLogReader;
import org.apache.cassandra.service.StorageService;

import com.exemple.cdc.agent.commitlog.CommitLogProcess;
import com.exemple.cdc.agent.common.DirectoryWatcher;
import com.exemple.cdc.agent.core.commitlog.CommitLogModule;
import com.exemple.cdc.agent.core.commitlog.DaggerCommitLogComponent;
import com.exemple.cdc.agent.event.EventProducer;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class AgentProcess {

    private final Path cdcLogPath;

    private final CommitLogReader commitLogReader;

    private final CommitLogReadHandler commitLogReadHandler;

    public AgentProcess(Path cdcLogPath, EventProducer eventProducer) {
        this.cdcLogPath = cdcLogPath;
        var component = DaggerCommitLogComponent.builder().commitLogModule(new CommitLogModule(eventProducer)).build();
        commitLogReader = component.commitLogReader();
        commitLogReadHandler = component.commitLogReadHandler();
    }

    public void start() {

        var commitLogExecutor = Executors.newSingleThreadExecutor();
        commitLogExecutor.submit(() -> {

            waitStorageServiceIsStarting();

            DirectoryWatcher.onChangeOrCreateFile(cdcLogPath, 0, file -> file.getAbsolutePath().endsWith("_cdc.idx"),
                    file -> new CommitLogProcess(file, commitLogReader, commitLogReadHandler).process());
        });

    }

    @SneakyThrows
    private static void waitStorageServiceIsStarting() {

        do {
            Thread.sleep(1_000);
        } while (StorageService.instance.getLocalHostUUID() == null);

    }

}
