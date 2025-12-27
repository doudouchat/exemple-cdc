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

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
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

import com.datastax.oss.driver.api.core.CqlSession;
import com.exemple.cdc.core.core.AgentTestConfiguration;
import com.exemple.cdc.core.core.cassandra.EmbeddedCassandraConfiguration;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = { EmbeddedCassandraConfiguration.class, AgentTestConfiguration.class })
@ActiveProfiles("test")
@TestPropertySource(properties = { "cassandra.agent=classpath:agent-exec.jar", "cassandra.loadAgent=false" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@Slf4j
class ReloadAgentIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private CqlSession session;

    @Autowired
    private KafkaConsumer<String, JsonNode> consumerEvent;

    @Autowired
    private GenericContainer<?> embeddedCassandra;

    @Autowired
    private KafkaContainer embeddedKafka;

    @Autowired
    private GenericContainer<?> embeddedZookeeper;

    @BeforeAll
    void createSchema() throws IOException {

        var schema = new FileSystemResource(ResourceUtils.getFile("classpath:script/schema.cql"));
        Arrays.stream(schema.getContentAsString(StandardCharsets.UTF_8).trim().split(";")).forEach(session::execute);
    }

    @Test
    @Order(1)
    void createEventBeforeLoadAgent() throws UnsupportedOperationException, IOException, InterruptedException {

        // Given perform
        session.execute("""
                        INSERT INTO test_event (id, date, application, version, event_type, data, user) VALUES (
                        48972a46-b48b-499f-aa63-534754497090,
                        '2023-12-01 13:00',
                        'app1',
                        'v1',
                        'CREATE_ACCOUNT',
                        '{"email": "other@gmail.com", "name": "Doe"}',
                        'jean.dupond'
                        );
                        """);

        // When perform agent
        var jvmOpts = new StringBuffer()
                .append("-javaagent:/tmp/lib/jacocoagent.jar")
                .append("=")
                .append("includes=com.exemple.cdc.*")
                .append(",destfile=/tmp/load/jacoco.exec");

        var stdOut = embeddedCassandra
                .execInContainer("java", jvmOpts.toString(), "-jar",
                        "tmp/lib/exemple-cdc-load-agent.jar",
                        "/exemple-cdc-agent.jar",
                        "conf=/tmp/conf/exemple-cdc.yml")
                .getStdout();

        // Then check load agent logs
        assertThat(stdOut).contains("Load CDC agent");

        // And check agent logs
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).contains("CDC agent started");
        });

        // And check event
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
            assertThat(records.iterator()).toIterable().last().satisfies(event -> {

                LOG.debug("received event {}:{}", event.key(), event.value().toPrettyString());

                assertThat(event.value()).isEqualTo(MAPPER.readTree(
                        """
                        {"email": "other@gmail.com", "name": "Doe", "id": "48972a46-b48b-499f-aa63-534754497090"}
                        """));
            });
        });

    }

    @Test
    @Order(2)
    void createEventAfterLoadAgent() {

        // When perform
        session.execute("""
                        INSERT INTO test_event (id, date, application, version, event_type, data, user) VALUES (
                        55d7566c-077f-4a2f-9b80-b91c7aad2853,
                        '2023-12-01 13:00',
                        'app1',
                        'v1',
                        'CREATE_ACCOUNT',
                        '{"email": "other@gmail.com", "name": "Doe"}',
                        'jean.dupond'
                        );
                        """);

        // Then check event
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
            assertThat(records.iterator()).toIterable().last().satisfies(event -> {

                LOG.debug("received event {}:{}", event.key(), event.value().toPrettyString());

                assertThat(event.value()).isEqualTo(MAPPER.readTree(
                        """
                        {"email": "other@gmail.com", "name": "Doe", "id": "55d7566c-077f-4a2f-9b80-b91c7aad2853"}
                        """));
            });
        });

    }

    @AfterAll
    void copyJacocoExec() throws IOException {

        try (var localJacocoFile = new FileOutputStream("target/jacoco-reload-it.exec")) {

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

        embeddedCassandra.copyFileFromContainer("/tmp/load/jacoco.exec", "target/jacoco-load-it.exec");
        embeddedCassandra.stop();
    }

    @AfterAll
    void closeContainer() {
        consumerEvent.close();
        embeddedKafka.stop();
        embeddedZookeeper.stop();
    }

}
