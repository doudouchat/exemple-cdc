package com.exemple.cdc.core.core.cassandra;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(EmbeddedCassandraConfigurationProperties.class)
@Slf4j
@RequiredArgsConstructor
public class EmbeddedCassandraConfiguration {

    private final EmbeddedCassandraConfigurationProperties cassandraProperties;

    @Bean
    public CassandraContainer embeddedCassandra(KafkaContainer kafkaContainer) throws IOException {

        var agent = ResourceUtils.getFile(cassandraProperties.getAgent()).getAbsolutePath();
        var lib = ResourceUtils.getFile(cassandraProperties.getLib()).getAbsolutePath();
        var conf = ResourceUtils.getFile(cassandraProperties.getConf()).getAbsolutePath();

        var jvmExtraOpts = new StringBuffer()
                .append("-javaagent:/tmp/lib/jacocoagent.jar")
                .append("=")
                .append("includes=com.exemple.cdc.*")
                .append(",output=tcpserver,address=*")
                .append(",classdumpdir=/tmp/agent/source");

        if (cassandraProperties.isLoadAgent()) {
            jvmExtraOpts
                    .append(" ")
                    .append("-javaagent:/exemple-cdc-agent.jar");
        }

        return new CassandraContainer("cassandra:" + cassandraProperties.getVersion())
                .withNetwork(kafkaContainer.getNetwork())
                .withExposedPorts(9042, 6300, 6301)
                .withCopyToContainer(MountableFile.forHostPath(agent), "/exemple-cdc-agent.jar")
                .withCopyToContainer(MountableFile.forHostPath(lib), "/tmp/lib")
                .withCopyToContainer(MountableFile.forHostPath(conf), "/tmp/conf")
                .withConfigurationOverride("conf/cassandra")
                .withEnv("JVM_EXTRA_OPTS", jvmExtraOpts.toString())
                .waitingFor(Wait.forLogMessage(".*Startup complete.*\\n", 1))
                .withLogConsumer(new Slf4jLogConsumer(LOG));
    }

}
