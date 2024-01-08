package com.exemple.cdc.core;

import java.nio.file.Path;
import java.util.concurrent.Executors;

import org.apache.cassandra.db.commitlog.CommitLogReadHandler;
import org.apache.cassandra.db.commitlog.CommitLogReader;
import org.apache.cassandra.service.StorageService;

import com.exemple.cdc.core.commitlog.CommitLogProcess;
import com.exemple.cdc.core.common.DirectoryWatcher;
import com.exemple.cdc.core.configuration.commitlog.CommitLogModule;
import com.exemple.cdc.core.configuration.commitlog.DaggerCommitLogComponent;
import com.exemple.cdc.core.event.EventProducer;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class ProcessRun {

    private final Path cdcLogPath;

    private final CommitLogReader commitLogReader;

    private final CommitLogReadHandler commitLogReadHandler;

    public ProcessRun(Path cdcLogPath, EventProducer eventProducer) {
        this.cdcLogPath = cdcLogPath;
        var component = DaggerCommitLogComponent.builder().commitLogModule(new CommitLogModule(eventProducer)).build();
        commitLogReader = component.commitLogReader();
        commitLogReadHandler = component.commitLogReadHandler();
    }

    public void start() {

        var commitLogExecutor = Executors.newSingleThreadExecutor();
        commitLogExecutor.submit(() -> {

            waitStorageServiceIsStarting();
            var watcher = new DirectoryWatcher(cdcLogPath, 0, file -> file.getAbsolutePath().endsWith("_cdc.idx"),
                    file -> new CommitLogProcess(file, commitLogReader, commitLogReadHandler).process());

            watcher.onCreateFile();
        });

    }

    @SneakyThrows
    private static void waitStorageServiceIsStarting() {

        do {
            Thread.sleep(1_000);
        } while (StorageService.instance.getLocalHostUUID() == null);

    }

}
