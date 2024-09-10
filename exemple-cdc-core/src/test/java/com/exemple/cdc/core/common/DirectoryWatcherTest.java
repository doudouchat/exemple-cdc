package com.exemple.cdc.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

public class DirectoryWatcherTest {

    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class createOrChangeOrDeleteFile {

        private Path directory;

        private File testFile;

        protected BlockingQueue<Path> records;

        @BeforeAll
        void initWatch() throws IOException {

            directory = Files.createTempDirectory("test");
            records = new LinkedBlockingQueue<>();

            var watcher = new DirectoryWatcher(directory, 5, f -> true, f -> records.add(f.toPath()));

            watcher.onCreateFile();
        }

        @Test
        @Order(1)
        void createFile() throws IOException, InterruptedException {

            // When create file
            testFile = File.createTempFile("test", "", directory.toFile());

            // Then check event
            var actualPath = records.poll(2, TimeUnit.SECONDS);
            assertThat(actualPath).isEqualTo(testFile.toPath());

        }

        @Test
        @Order(2)
        void modifyFile() throws IOException, InterruptedException {

            // When modify file
            FileUtils.write(testFile, "test", Charset.defaultCharset());

            // Then check event
            var actualPath = records.poll(2, TimeUnit.SECONDS);
            assertThat(actualPath).isNull();

        }

        @Test
        @Order(3)
        void deleteFile() throws IOException, InterruptedException {

            // When delete file
            FileUtils.delete(testFile);

            // Then check event
            var actualPath = records.poll(2, TimeUnit.SECONDS);
            assertThat(actualPath).isNull();

        }

        @Test
        @Order(4)
        void createAgainFile() throws IOException, InterruptedException {

            // When delete file
            Files.createFile(testFile.toPath());

            // Then check event
            var actualPath = records.poll(2, TimeUnit.SECONDS);
            assertThat(actualPath).isEqualTo(testFile.toPath());

        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class createBeforeStartListener {

        private Path directory;

        private File testFile;

        private DirectoryWatcher watcher;

        protected BlockingQueue<Path> records;

        @BeforeAll
        void initWatch() throws IOException {

            directory = Files.createTempDirectory("test");
            records = new LinkedBlockingQueue<>();
            watcher = new DirectoryWatcher(directory, 5, f -> true, f -> records.add(f.toPath()));

        }

        @Test
        void createFile() throws IOException, InterruptedException {

            // Given create file
            testFile = File.createTempFile("test", "", directory.toFile());

            // When start
            watcher.onCreateFile();

            // Then check event
            var actualPath = records.poll(2, TimeUnit.SECONDS);
            assertThat(actualPath).isEqualTo(testFile.toPath());

        }

    }

}
