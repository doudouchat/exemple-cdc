package com.exemple.cdc.agent.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.KafkaContainer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.exemple.cdc.agent.common.CdcEvent;
import com.exemple.cdc.agent.core.AgentTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(classes = AgentTestConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class EventProducerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventProducer eventProducer;

    @Autowired
    private KafkaConsumer<String, JsonNode> consumerEvent;

    @Autowired
    private KafkaContainer kafkaContainer;

    @BeforeAll
    public void createSchema() throws IOException {

        Map<String, Object> data = Map.of(
                "bootstrap_servers", "localhost:" + kafkaContainer.getMappedPort(9093),
                "timeout", 10,
                "topics", Map.of("test", "topic_test"));

        var options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        var yaml = new Yaml(options);
        var writer = new FileWriter("target/test-classes/test.yml");
        yaml.dump(Map.of("kafka", data), writer);

        this.eventProducer = new EventProducer("target/test-classes/test.yml");
    }

    @Test
    void sendOneEvent() throws IOException {

        // Setup event
        var event = CdcEvent.builder()
                .resource("test")
                .eventType("CREATION")
                .origin("test")
                .originVersion("v1")
                .date(OffsetDateTime.now())
                .data((ObjectNode) MAPPER.readTree("{\n"
                        + "  \"email\" : \"test@gmail.com\",\n"
                        + "  \"name\" : \"Doe\",\n"
                        + "  \"id\" : \"e143f715-f14e-44b4-90f1-47246661eb7d\"\n"
                        + "}"))
                .build();

        // When perform
        this.eventProducer.send(event);

        // Then check event
        ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(records.iterator()).toIterable().last().satisfies(record -> {

                LOG.debug("received event {}:{}", record.key(), record.value().toPrettyString());

                assertThat(record.value()).isEqualTo(MAPPER.readTree("{\n"
                        + "  \"email\" : \"test@gmail.com\",\n"
                        + "  \"name\" : \"Doe\",\n"
                        + "  \"id\" : \"e143f715-f14e-44b4-90f1-47246661eb7d\"\n"
                        + "}"));
            });
        });

    }

    @Test
    void sendMultiEvents() throws IOException {

        // Setup event
        var event = CdcEvent.builder()
                .resource("test")
                .eventType("CREATION")
                .origin("test")
                .originVersion("v1")
                .date(OffsetDateTime.now())
                .data((ObjectNode) MAPPER.readTree("{\n"
                        + "  \"email\" : \"test@gmail.com\",\n"
                        + "  \"name\" : \"Doe\",\n"
                        + "  \"id\" : \"9e933e5e-34ff-4941-ba34-8af3e8965c22\"\n"
                        + "}"))
                .build();

        // When perform
        ExecutorService executorService = new ThreadPoolExecutor(10, 1000, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> eventProducer.send(event));
        }

        // Then check event
        ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(records).hasSizeGreaterThan(1);
        });

    }

}
