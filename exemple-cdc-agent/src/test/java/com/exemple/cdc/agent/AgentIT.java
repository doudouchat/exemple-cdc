package com.exemple.cdc.agent;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.GenericContainer;

import com.datastax.oss.driver.api.core.CqlSession;
import com.exemple.cdc.agent.core.AgentTestConfiguration;

@SpringBootTest(classes = AgentTestConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentIT {

    @Autowired
    private CqlSession session;

    @Autowired
    private GenericContainer<?> container;

    @BeforeAll
    public void createSchema() throws IOException {

        var schema = ResourceUtils.getFile("classpath:script/schema.cql");
        Arrays.stream(FileUtils.readFileToString(schema, StandardCharsets.UTF_8).trim().split(";")).forEach(session::execute);

    }

    @Test
    void test() throws IOException {

        // Setup insert Event
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

    }

    @AfterAll
    public void copyJacocoExec() throws IOException {

        try (var localJacocoFile = new FileOutputStream("target/jacoco-it.exec")) {

            try (var socket = new Socket(InetAddress.getByName(container.getHost()), container.getMappedPort(6300))) {

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
