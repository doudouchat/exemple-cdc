package com.exemple.cdc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(classes = { EmbeddedCassandraConfiguration.class, AgentTestConfiguration.class })
@ActiveProfiles("test")
@TestPropertySource(properties = "cassandra.agent=classpath:agent-exec.jar")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class AgentIT {

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
    public void createSchema() throws IOException {

        var schema = new FileSystemResource(ResourceUtils.getFile("classpath:script/schema.cql"));
        Arrays.stream(schema.getContentAsString(StandardCharsets.UTF_8).trim().split(";")).forEach(session::execute);
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CreateEvent {

        @Test
        @Order(1)
        void createFirstEvent() {

            // When perform
            session.execute("""
                            INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (
                             e143f715-f14e-44b4-90f1-47246661eb7d,
                             '2023-12-01 12:00',
                             'app1',
                             'v1',
                             'CREATE_ACCOUNT',
                             '{"email": "test@gmail.com", "name": "Doe"}',
                             '2023-12-01'
                             );
                            """);

            // Then check event
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
                assertThat(records.iterator()).toIterable().last().satisfies(event -> {

                    LOG.debug("received event {}:{}", event.key(), event.value().toPrettyString());

                    assertAll(
                            () -> assertThat(event.key()).isEqualTo("e143f715-f14e-44b4-90f1-47246661eb7d"),
                            () -> assertThat(event.value()).isEqualTo(MAPPER.readTree(
                                    """
                                    {"email": "test@gmail.com", "name": "Doe", "id": "e143f715-f14e-44b4-90f1-47246661eb7d"}
                                    """)));
                });
            });

        }

        @Test
        @Order(2)
        void createSecondEvent() {

            // When perform
            session.execute("""
                            INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (
                            7977b564-5f53-4296-bc0a-438900e089ad,
                            '2023-12-01 13:00',
                            'app1',
                            'v1',
                            'CREATE_ACCOUNT',
                            '{"email": "other@gmail.com", "name": "Doe"}',
                            '2023-12-01'
                            );
                             """);

            // Then check event
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
                assertThat(records.iterator()).toIterable().last().satisfies(event -> {

                    LOG.debug("received event {}:{}", event.key(), event.value().toPrettyString());

                    assertAll(
                            () -> assertThat(event.key()).isEqualTo("7977b564-5f53-4296-bc0a-438900e089ad"),
                            () -> assertThat(event.value()).isEqualTo(MAPPER.readTree(
                                    """
                                    {"email": "other@gmail.com", "name": "Doe", "id": "7977b564-5f53-4296-bc0a-438900e089ad"}
                                    """)));
                });
            });

        }

        @Test
        @Order(3)
        void createEventInBatch() {

            // When perform
            session.execute("""
                            BEGIN BATCH
                            INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (
                            547700ac-824e-45f4-a6ee-35773259a8c3,
                            '2023-12-01 12:00',
                            'app1',
                            'v1',
                            'CREATE_ACCOUNT',
                            '{"email": "test@gmail.com", "name": "Doe"}',
                            '2023-12-01'
                            );
                            INSERT INTO test_other (id) VALUES (547700ac-824e-45f4-a6ee-35773259a8c3);
                            APPLY BATCH
                            """);

            // Then check event
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
                assertThat(records.iterator()).toIterable().last().satisfies(event -> {

                    LOG.debug("received event {}:{}", event.key(), event.value().toPrettyString());

                    assertAll(
                            () -> assertThat(event.key()).isEqualTo("547700ac-824e-45f4-a6ee-35773259a8c3"),
                            () -> assertThat(event.value()).isEqualTo(MAPPER.readTree(
                                    """
                                    {"email": "test@gmail.com", "name": "Doe", "id": "547700ac-824e-45f4-a6ee-35773259a8c3"}
                                    """)));
                });
            });
        }

        @Test
        @Order(4)
        void createEventWithoutData() {

            // When perform
            session.execute("""
                            INSERT INTO test_event (id, date) VALUES (4c95bfb2-5190-41a5-bfe0-598d838fcd83,'2023-12-01 13:00');
                            """);

            // Then check event
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
                assertThat(records.iterator()).toIterable().last().satisfies(event -> {

                    LOG.debug("received event {}:{}", event.key(), event.value().toPrettyString());

                    assertAll(
                            () -> assertThat(event.key()).isEqualTo("4c95bfb2-5190-41a5-bfe0-598d838fcd83"),
                            () -> assertThat(event.value()).isEqualTo(MAPPER.readTree(
                                    """
                                    {"id": "4c95bfb2-5190-41a5-bfe0-598d838fcd83"}
                                    """)));
                });
            });

        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UpdateEvent {

        @BeforeAll
        void createEvent() {

            session.execute("""
                            INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (
                            50f9e704-b84c-4225-8883-b7b5d6114634,
                            '2023-12-01 12:00',
                            'app1',
                            'v1',
                            'CREATE_ACCOUNT',
                            '{"email": "test@gmail.com", "name": "Doe"}',
                            '2023-12-01'
                            );
                            """);

        }

        @Test
        void updateEvent() {

            // When perform
            session.execute("""
                            UPDATE test_event SET data = '{"email": "test2@gmail.com", "name": "Doe"}'
                            WHERE id = 50f9e704-b84c-4225-8883-b7b5d6114634 AND date = '2023-12-01 12:00'
                            """);

            // Then check logs
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).contains("Only Insert is expected");
            });

        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DeleteEvent {

        @BeforeAll
        void createEvent() {

            session.execute("""
                            INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (
                            4722f55d-b33d-411b-9fdf-b66fb17820aa,
                            '2023-12-01 12:00',
                            'app1',
                            'v1',
                            'CREATE_ACCOUNT',
                            '{"email": "test@gmail.com", "name": "Doe"}',
                            '2023-12-01'
                             );
                            """);

        }

        @Test
        void deleteEvent() {

            // When delete Event
            session.execute("""
                            DELETE FROM test_event WHERE id = 4722f55d-b33d-411b-9fdf-b66fb17820aa AND date = '2023-12-01 12:00'
                            """);

            // Then check logs
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).contains("Only Insert is expected");
            });

        }

    }

    @AfterAll
    public void copyJacocoExec() throws IOException {

        try (var localJacocoFile = new FileOutputStream("target/jacoco-it.exec")) {

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
        embeddedCassandra.stop();
    }

    @AfterAll
    public void closeContainer() {
        consumerEvent.close();
        embeddedKafka.stop();
        embeddedZookeeper.stop();
    }

}
