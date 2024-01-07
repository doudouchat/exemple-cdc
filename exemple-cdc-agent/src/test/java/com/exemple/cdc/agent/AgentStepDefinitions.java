package com.exemple.cdc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;

import io.cucumber.java.en.Given;

public class AgentStepDefinitions {

    @Given("stop agent")
    public void stop() {

    }

    @Given("reload agent")
    public void reload() throws IOException, UnsupportedOperationException, InterruptedException {

        var jvmOpts = new StringBuffer()
                .append("-javaagent:/tmp/lib/jacocoagent.jar")
                .append("=")
                .append("includes=com.exemple.cdc.*")
                .append(",classdumpdir=/tmp/load/source")
                .append(",destfile=/tmp/load/jacoco.exec");

        var dockerClient = DockerClientFactory.instance().client();
        var cmdResponse = dockerClient.execCreateCmd("cassandra-1")
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("java", jvmOpts.toString(), "-jar",
                        "tmp/lib/exemple-cdc-load-agent.jar", "/exemple-cdc-agent.jar")
                .exec();

        var stdoutConsumer = new ToStringConsumer();
        var stderrConsumer = new ToStringConsumer();

        try (FrameConsumerResultCallback callback = new FrameConsumerResultCallback()) {
            callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer);
            callback.addConsumer(OutputFrame.OutputType.STDERR, stderrConsumer);

            dockerClient.execStartCmd(cmdResponse.getId()).exec(callback).awaitCompletion();
        }

        assertThat(stdoutConsumer.toString(StandardCharsets.UTF_8)).contains("Load CDC agent");

    }

}
