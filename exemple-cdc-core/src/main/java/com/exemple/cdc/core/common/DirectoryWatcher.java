package com.exemple.cdc.core.common;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DirectoryWatcher {

    @SneakyThrows
    public static void onChangeOrCreateFile(Path directory, long intervalMillis, Consumer<File> onChangeOrCreate) {

        onChangeOrCreateFile(directory, intervalMillis, f -> true, onChangeOrCreate);
    }

    @SneakyThrows
    public static void onChangeOrCreateFile(Path directory, long intervalMillis, FileFilter filter, Consumer<File> onChangeOrCreate) {

        var observer = new FileAlterationObserver(directory.toString(), filter);
        var monitor = new FileAlterationMonitor(intervalMillis);
        var listener = new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                onChangeOrCreate.accept(file);
            }

            @Override
            public void onFileChange(File file) {
                onChangeOrCreate.accept(file);
            }
        };
        observer.addListener(listener);
        monitor.addObserver(observer);
        monitor.start();
    }

}
