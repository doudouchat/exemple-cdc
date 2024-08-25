package com.exemple.cdc.core;

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
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.exemple.cdc.core.commitlog.CommitLogProcess;
import com.exemple.cdc.core.core.AgentTestConfiguration;
import com.exemple.cdc.core.core.cassandra.EmbeddedCassandraConfiguration;

@SpringBootTest(classes = { EmbeddedCassandraConfiguration.class, AgentTestConfiguration.class })
@ActiveProfiles("test")
@TestPropertySource(properties = "cassandra.agent=classpath:agent-mock-exec.jar")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class AgentMockIT {

    @Autowired
    private CqlSession session;

    @Autowired
    private GenericContainer<?> embeddedCassandra;

    @Autowired
    private KafkaContainer embeddedKafka;

    @Autowired
    private GenericContainer<?> embeddedZookeeper;

    @BeforeAll
    public void createSchema() throws IOException {

        var schema = new FileSystemResource(ResourceUtils.getFile("classpath:script/schema.cql"));
        Arrays.stream(schema.getContentAsString(StandardCharsets.UTF_8).trim().split(";")).forEach(session::execute);
    }

    @Nested
    @Order(1)
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
                executorService.submit(() -> insertEvent(UUID.randomUUID(), "ANY_EVENT"));
            }

            // Then check logs
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).contains("Finished reading /opt/cassandra/data/cdc_raw/CommitLog");

            });

            // And check missing commit log
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var result = embeddedCassandra.execInContainer("ls", "/opt/cassandra/data/cdc_raw");
                assertThat(result.getStdout()).doesNotContainPattern("CommitLog-\\d+-" + segmentId + ".log");
                assertThat(result.getStdout()).doesNotContain("CommitLog-\\d+-" + segmentId + "_cdc.idx");

            });

            executorService.awaitTermination(5, TimeUnit.SECONDS);
            executorService.shutdown();
        }

    }

    @Nested
    @Order(2)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(OrderAnnotation.class)
    class CreateExceptionEvents {

        private LocalDateTime failureEvent;

        private LocalDateTime successEvent;

        @Test
        @Order(0)
        void createOneEvent() {

            // when perform update
            var event = insertEvent(UUID.randomUUID(), "SUCCESS_EVENT");

            // Then check logs
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).containsOnlyOnce("SUCCESS EVENT " + event);
            });
        }

        @Test
        @Order(1)
        void createOneExceptionEvent() {

            // when perform update
            this.failureEvent = insertEvent(UUID.randomUUID(), "FAILURE_EVENT");

            // Then check logs
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).containsOnlyOnce("FAILURE EVENT " + this.failureEvent);
            });
        }

        @Test
        @Order(2)
        void reloadAgentWithFailure() throws UnsupportedOperationException, IOException, InterruptedException {

            // Given success event
            this.successEvent = insertEvent(UUID.randomUUID(), "SUCCESS_EVENT");

            // When perform agent
            var jvmOpts = new StringBuffer()
                    .append("-javaagent:/tmp/lib/jacocoagent.jar")
                    .append("=")
                    .append("includes=com.exemple.cdc.*")
                    .append(",destfile=/tmp/load/jacoco.exec");
            embeddedCassandra.execInContainer("java", jvmOpts.toString(), "-jar",
                    "tmp/lib/exemple-cdc-load-agent.jar",
                    "/exemple-cdc-agent.jar",
                    "nope");

            // Then check logs
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(StringUtils.countMatches(embeddedCassandra.getLogs(OutputType.STDOUT), "FAILURE EVENT " + this.failureEvent)).isEqualTo(2);
            });
        }

        @Test
        @Order(3)
        void reloadAgentWithSuccess() throws UnsupportedOperationException, IOException, InterruptedException {

            // When perform agent
            var jvmOpts = new StringBuffer()
                    .append("-javaagent:/tmp/lib/jacocoagent.jar")
                    .append("=")
                    .append("includes=com.exemple.cdc.*")
                    .append(",destfile=/tmp/load/jacoco.exec");
            embeddedCassandra.execInContainer("java", jvmOpts.toString(), "-jar",
                    "tmp/lib/exemple-cdc-load-agent.jar",
                    "/exemple-cdc-agent.jar",
                    "force_success=true");

            // Then check logs
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(StringUtils.countMatches(embeddedCassandra.getLogs(OutputType.STDOUT), "SUCCESS EVENT " + this.failureEvent)).isEqualTo(1);
                assertThat(StringUtils.countMatches(embeddedCassandra.getLogs(OutputType.STDOUT), "SUCCESS EVENT " + this.successEvent)).isEqualTo(1);
            });
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
        embeddedCassandra.copyFileFromContainer("/tmp/load/jacoco.exec", "target/jacoco-mock-reload-it.exec");
        embeddedCassandra.stop();
    }

    @AfterAll
    public void closeContainer() {
        embeddedKafka.stop();
        embeddedZookeeper.stop();
    }

    private LocalDateTime insertEvent(UUID id, String eventType) {

        var eventDate = LocalDateTime.now();

        session.execute("INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (\n"
                + id + ",\n"
                + "'" + eventDate.toString() + "',\n"
                + "'app1',\n"
                + "'v1',\n"
                + "'" + eventType + "',\n"
                + "'{\n"
                + "  \"email\": \"other@gmail.com\",\n"
                + "  \"name\": \"Doe\"\n"
                + "}',\n"
                + "'2023-12-01'\n"
                + ");");

        return eventDate;
    }

}
