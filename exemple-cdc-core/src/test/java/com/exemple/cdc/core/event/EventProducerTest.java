package com.exemple.cdc.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.exemple.cdc.core.common.CdcEvent;
import com.exemple.cdc.core.configuration.kafka.KafkaProducerModule;
import com.exemple.cdc.core.configuration.zookeeper.ZookeeperClientModule;
import com.exemple.cdc.core.core.AgentTestConfiguration;
import com.exemple.cdc.core.event.EventProducer;
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

    @Autowired
    private GenericContainer embeddedZookeeper;

    @BeforeAll
    public void createSchema() throws IOException {

        Map<String, Object> kafka = Map.of(
                "bootstrap_servers", "localhost:" + kafkaContainer.getMappedPort(9093),
                "timeout", 10,
                "topics", Map.of("test", "topic_test"));

        Map<String, Object> zookeeper = Map.of(
                "host", "localhost:" + embeddedZookeeper.getMappedPort(2181),
                "session_timeout", 30000,
                "connection_timeout", 10000,
                "retry", 3,
                "eventTTL", 10000);

        var options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        var yaml = new Yaml(options);
        var writer = new FileWriter("target/test-classes/test.yml");
        yaml.dump(Map.of("kafka", kafka, "zookeeper", zookeeper), writer);

        var zookeeperClientModule = new ZookeeperClientModule("target/test-classes/test.yml");
        var kafkaProducerModule = new KafkaProducerModule("target/test-classes/test.yml");

        this.eventProducer = new EventProducer(
                kafkaProducerModule.kafkaProducer(),
                kafkaProducerModule.kafkaProperties(),
                zookeeperClientModule.zookeeperClient(),
                zookeeperClientModule.zookeeperProperties());
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
    void sendTwoEvents() throws IOException {

        // Setup one event
        var event1 = CdcEvent.builder()
                .resource("test")
                .eventType("UPDATE")
                .origin("test")
                .originVersion("v1")
                .date(OffsetDateTime.now())
                .data((ObjectNode) MAPPER.readTree("{\n"
                        + "  \"email\" : \"test@gmail.com\",\n"
                        + "  \"name\" : \"Doe\",\n"
                        + "  \"id\" : \"2bc572fc-b6cd-4763-8ca2-6225689473b3\"\n"
                        + "}"))
                .build();

        // And second event
        var event2 = CdcEvent.builder()
                .resource("test")
                .eventType("UPDATE")
                .origin("test")
                .originVersion("v1")
                .date(OffsetDateTime.now().plusSeconds(1))
                .data((ObjectNode) MAPPER.readTree("{\n"
                        + "  \"email\" : \"test@gmail.com\",\n"
                        + "  \"name\" : \"Doe\",\n"
                        + "  \"id\" : \"2bc572fc-b6cd-4763-8ca2-6225689473b3\"\n"
                        + "}"))
                .build();

        // When perform first event
        this.eventProducer.send(event1);

        // And perform second event
        this.eventProducer.send(event2);

        // Then check event
        var counter = new AtomicInteger();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {

            ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(2));
            counter.addAndGet(records.count());
            assertThat(records).isEmpty();
        });
        assertThat(counter.intValue()).isEqualTo(2);

    }

    @Test
    void sendOneEventFailsBecauseResourceIsIncorrect() throws IOException {

        // Setup event
        var event = CdcEvent.builder()
                .resource("unknown")
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
        var throwable = catchThrowable(() -> this.eventProducer.send(event));

        // Then check none exception
        assertThat(throwable).isInstanceOf(IllegalStateException.class).hasMessage("unknown has not any topic");

    }

    @Test
    void sendMultiEvents() throws IOException, InterruptedException {

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
        var executorService = new ThreadPoolExecutor(5, 100, 1000, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> eventProducer.send(event));
        }
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then check event
        var counter = new AtomicInteger();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {

            ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(2));
            counter.addAndGet(records.count());

            LOG.debug("count events {}", counter.intValue());

            assertThat(records).isEmpty();
        });
        assertThat(counter.intValue()).isEqualTo(1);

    }

}
