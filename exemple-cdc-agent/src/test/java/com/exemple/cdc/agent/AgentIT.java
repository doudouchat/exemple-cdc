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

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import com.datastax.oss.driver.api.core.CqlSession;
import com.exemple.cdc.agent.core.AgentTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(classes = AgentTestConfiguration.class)
@ActiveProfiles("test")
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

    @BeforeAll
    public void createSchema() throws IOException {

        var schema = new FileSystemResource(ResourceUtils.getFile("classpath:script/schema.cql"));
        Arrays.stream(schema.getContentAsString(StandardCharsets.UTF_8).trim().split(";")).forEach(session::execute);
    }

    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    class CreateEvent {

        @Test
        @Order(1)
        void createFirstEvent() {

            // When perform
            session.execute("INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (\n"
                    + "e143f715-f14e-44b4-90f1-47246661eb7d,\n"
                    + "'2023-12-01 12:00',\n"
                    + "'app1',\n"
                    + "'v1',\n"
                    + "'CREATE_ACCOUNT',\n"
                    + "'{\n"
                    + "  \"email\": \"test@gmail.com\",\n"
                    + "  \"name\": \"Doe\"\n"
                    + "}',\n"
                    + "'2023-12-01'\n"
                    + ");");

            // Then check event
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
                assertThat(records.iterator()).toIterable().last().satisfies(record -> {

                    LOG.debug("received event{}", record.value().toPrettyString());

                    assertThat(record.value()).isEqualTo(MAPPER.readTree("{\n"
                            + "  \"email\" : \"test@gmail.com\",\n"
                            + "  \"name\" : \"Doe\",\n"
                            + "  \"id\" : \"e143f715-f14e-44b4-90f1-47246661eb7d\"\n"
                            + "}"));
                });
            });

        }

        @Test
        @Order(2)
        void createSecondEvent() {

            // When perform
            session.execute("INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (\n"
                    + "7977b564-5f53-4296-bc0a-438900e089ad,\n"
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

            // Then check event
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
                assertThat(records.iterator()).toIterable().last().satisfies(record -> {

                    LOG.debug("received event{}", record.value().toPrettyString());

                    assertThat(record.value()).isEqualTo(MAPPER.readTree("{\n"
                            + "  \"email\" : \"other@gmail.com\",\n"
                            + "  \"name\" : \"Doe\",\n"
                            + "  \"id\" : \"7977b564-5f53-4296-bc0a-438900e089ad\"\n"
                            + "}"));
                });
            });

        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UpdateEvent {

        @BeforeAll
        void createEvent() {

            session.execute("INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (\n"
                    + "50f9e704-b84c-4225-8883-b7b5d6114634,\n"
                    + "'2023-12-01 12:00',\n"
                    + "'app1',\n"
                    + "'v1',\n"
                    + "'CREATE_ACCOUNT',\n"
                    + "'{\n"
                    + "  \"email\": \"test@gmail.com\",\n"
                    + "  \"name\": \"Doe\"\n"
                    + "}',\n"
                    + "'2023-12-01'\n"
                    + ");");

        }

        @Test
        void updateEvent() {

            // When perform
            session.execute("UPDATE test_event \n"
                    + "SET data = '{\n"
                    + "  \"email\": \"test2@gmail.com\",\n"
                    + "  \"name\": \"Doe\"\n"
                    + "}'\n"
                    + "WHERE id = 50f9e704-b84c-4225-8883-b7b5d6114634 AND date = '2023-12-01 12:00'");

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

            session.execute("INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (\n"
                    + "4722f55d-b33d-411b-9fdf-b66fb17820aa,\n"
                    + "'2023-12-01 12:00',\n"
                    + "'app1',\n"
                    + "'v1',\n"
                    + "'CREATE_ACCOUNT',\n"
                    + "'{\n"
                    + "  \"email\": \"test@gmail.com\",\n"
                    + "  \"name\": \"Doe\"\n"
                    + "}',\n"
                    + "'2023-12-01'\n"
                    + ");");

        }

        @Test
        void deleteEvent() {

            // When delete Event
            session.execute("DELETE \n"
                    + "FROM test_event \n"
                    + "WHERE id = 4722f55d-b33d-411b-9fdf-b66fb17820aa AND date = '2023-12-01 12:00'");

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
    }

}
