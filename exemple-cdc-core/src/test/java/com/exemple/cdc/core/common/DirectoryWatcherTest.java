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

import com.exemple.cdc.core.common.DirectoryWatcher;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

public class DirectoryWatcherTest {

    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class createOrChangeFile {

        private Path directory;

        private File testFile;

        protected BlockingQueue<Path> records;

        @BeforeAll
        void initWatch() throws IOException {

            directory = Files.createTempDirectory("test");
            records = new LinkedBlockingQueue<>();

            DirectoryWatcher.onChangeOrCreateFile(directory, 5, f -> records.add(f.toPath()));
        }

        @Test
        @Order(1)
        void createFile() throws IOException, InterruptedException {

            // When create file
            testFile = File.createTempFile("test", "", directory.toFile());

            // Then check event
            var actualPath = records.poll(10, TimeUnit.SECONDS);
            assertThat(actualPath).isEqualTo(testFile.toPath());

        }

        @Test
        @Order(2)
        void modifyFile() throws IOException, InterruptedException {

            // When modify file
            FileUtils.write(testFile, "test", Charset.defaultCharset());

            // Then check event
            var actualPath = records.poll(10, TimeUnit.SECONDS);
            assertThat(actualPath).isEqualTo(testFile.toPath());

        }

    }

}
