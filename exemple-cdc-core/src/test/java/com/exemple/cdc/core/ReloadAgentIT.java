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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.OutputFrame.OutputType;

import com.datastax.oss.driver.api.core.CqlSession;
import com.exemple.cdc.core.core.AgentTestConfiguration;
import com.exemple.cdc.core.core.cassandra.EmbeddedCassandraConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(classes = { EmbeddedCassandraConfiguration.class, AgentTestConfiguration.class })
@ActiveProfiles("test")
@TestPropertySource(properties = { "cassandra.agent=classpath:agent-exec.jar", "cassandra.loadAgent=false" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
    public void startAgent() throws IOException, UnsupportedOperationException, InterruptedException {

        var jvmOpts = new StringBuffer()
                .append("-javaagent:/tmp/lib/jacocoagent.jar")
                .append("=")
                .append("includes=com.exemple.cdc.*")
                .append(",destfile=/tmp/load/jacoco.exec");

        var stdOut = embeddedCassandra
                .execInContainer("java", jvmOpts.toString(), "-jar", "tmp/lib/exemple-cdc-load-agent.jar", "/exemple-cdc-agent.jar").getStdout();

        assertThat(stdOut).contains("Load CDC agent");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(embeddedCassandra.getLogs(OutputType.STDOUT)).contains("CDC agent started");
        });
    }

    @BeforeAll
    public void createSchema() throws IOException {

        var schema = new FileSystemResource(ResourceUtils.getFile("classpath:script/schema.cql"));
        Arrays.stream(schema.getContentAsString(StandardCharsets.UTF_8).trim().split(";")).forEach(session::execute);
    }

    @Test
    void createEventAfterLoadAgent() {

        // When perform
        session.execute("INSERT INTO test_event (id, date, application, version, event_type, data, local_date) VALUES (\n"
                + "55d7566c-077f-4a2f-9b80-b91c7aad2853,\n"
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
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> {
            ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
            assertThat(records.iterator()).toIterable().last().satisfies(record -> {

                LOG.debug("received event {}:{}", record.key(), record.value().toPrettyString());

                assertThat(record.value()).isEqualTo(MAPPER.readTree("{\n"
                        + "  \"email\" : \"other@gmail.com\",\n"
                        + "  \"name\" : \"Doe\",\n"
                        + "  \"id\" : \"55d7566c-077f-4a2f-9b80-b91c7aad2853\"\n"
                        + "}"));
            });
        });

    }

    @AfterAll
    public void copyJacocoExec() throws IOException {

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
    public void closeContainer() {
        consumerEvent.close();
        embeddedKafka.stop();
        embeddedZookeeper.stop();
    }

}
