package com.exemple.cdc.core.common;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public final class DirectoryWatcher {

    private final Path directory;

    private final long intervalMillis;

    private final FileFilter filter;

    private final Consumer<File> consumerFile;

    private final Set<String> runningConsumer = new HashSet<>();

    @SneakyThrows
    public void onCreateFile() {

        var observer = FileAlterationObserver.builder()
                .setFile(directory.toString())
                .setFileFilter(filter)
                .get();
        var monitor = new FileAlterationMonitor(intervalMillis);
        var listener = new FileAlterationListenerAdaptor() {

            @Override
            public void onStop(FileAlterationObserver observer) {

                Arrays.stream(observer.getDirectory().listFiles(filter)).forEach(file -> accept(file));
            }

            @Override
            public void onFileCreate(File file) {

                accept(file);
            }

            @Override
            public void onFileDelete(File file) {

                runningConsumer.remove(file.getName());
            }
        };
        observer.addListener(listener);
        monitor.addObserver(observer);
        monitor.start();
    }

    private void accept(File file) {

        synchronized (this) {
            if (runningConsumer.contains(file.getName())) {
                return;

            }
            runningConsumer.add(file.getName());
        }

        consumerFile.accept(file);
    }

}
