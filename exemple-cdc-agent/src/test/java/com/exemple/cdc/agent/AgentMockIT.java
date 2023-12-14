package com.exemple.cdc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import com.datastax.oss.driver.api.core.CqlSession;
import com.exemple.cdc.agent.commitlog.CommitLogProcess;
import com.exemple.cdc.agent.core.AgentTestConfiguration;
import com.exemple.cdc.agent.core.cassandra.EmbeddedCassandraConfiguration;

@SpringBootTest(classes = { EmbeddedCassandraConfiguration.class, AgentTestConfiguration.class })
@ActiveProfiles("test")
@TestPropertySource(properties = "cassandra.agent=classpath:agent-mock-exec.jar")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentMockIT {

    @Autowired
    private CqlSession session;

    @Autowired
    private GenericContainer<?> embeddedCassandra;

    @BeforeAll
    public void createSchema() throws IOException {

        var schema = new FileSystemResource(ResourceUtils.getFile("classpath:script/schema.cql"));
        Arrays.stream(schema.getContentAsString(StandardCharsets.UTF_8).trim().split(";")).forEach(session::execute);
    }

    @Nested
    class CreateMultiEvents {

        @Test
        void createMultiEvents() throws InterruptedException, IOException {

            // Setup segmentId
            var ls = embeddedCassandra.execInContainer("ls", "/opt/cassandra/data/cdc_raw").getStdout();
            var logsMatcher = CommitLogProcess.FILENAME_REGEX_PATTERN.matcher(ls);

            assert logsMatcher.lookingAt() : ls + " doesn't match ";

            var segmentId = logsMatcher.group(1);

            // when perform multiple update
            var executorService = new ThreadPoolExecutor(5, 1000, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

            for (int i = 0; i < 6000; i++) {
                executorService.submit(() -> insertEvent(UUID.randomUUID()));
            }

            // Then check logs
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).contains("Finished reading /opt/cassandra/data/cdc_raw/CommitLog");

            });

            // And check missing commit log
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var result = embeddedCassandra.execInContainer("ls", "/opt/cassandra/data/cdc_raw");
                assertThat(result.getStdout()).doesNotContainPattern("CommitLog-\\d+-" + segmentId + ".log");
                assertThat(result.getStdout()).doesNotContain("CommitLog-\\d+-" + segmentId + "_cdc.idx");

            });

            executorService.awaitTermination(5, TimeUnit.SECONDS);
            executorService.shutdown();
        }

        private void insertEvent(UUID id) {

            session.execute("INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (\n"
                    + id + ",\n"
                    + "'2023-12-01 13:00',\n"
                    + "'app1',\n"
                    + "'v1',\n"
                    + "'CREATE_ACCOUNT',\n"
                    + "'{\n"
                    + "  \"email\": \"other@gmail.com\",\n"
                    + "  \"name\": \"Doe\"\n"
                    + "}',\n"
                    + "'2023-12-01'\n"
                    + ");");
        }

    }

    @AfterAll
    public void copyJacocoExec() throws IOException {

        try (var localJacocoFile = new FileOutputStream("target/jacoco-mock-it.exec")) {

            try (var socket = new Socket(InetAddress.getByName(embeddedCassandra.getHost()), embeddedCassandra.getMappedPort(6300))) {

                var writer = new RemoteControlWriter(socket.getOutputStream());
                writer.visitDumpCommand(true, false);

                var reader = new RemoteControlReader(socket.getInputStream());

                var localWriter = new ExecutionDataWriter(localJacocoFile);
                reader.setSessionInfoVisitor(localWriter);
                reader.setExecutionDataVisitor(localWriter);
                reader.read();

            }
        }
    }

}
