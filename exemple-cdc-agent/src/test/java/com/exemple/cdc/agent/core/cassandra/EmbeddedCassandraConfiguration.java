package com.exemple.cdc.agent.core.cassandra;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(EmbeddedCassandraConfigurationProperties.class)
@Slf4j
@RequiredArgsConstructor
public class EmbeddedCassandraConfiguration {

    private final EmbeddedCassandraConfigurationProperties properties;

    @Bean(initMethod = "start")
    public CassandraContainer<?> embeddedServer() throws IOException {

        var agent = ResourceUtils.getFile(properties.getAgent()).getAbsolutePath();
        var lib = ResourceUtils.getFile(properties.getLib()).getAbsolutePath();

        var jvmExtraOpts = new StringBuffer()
                .append("-javaagent:/tmp/lib/jacocoagent.jar")
                .append("=")
                .append("includes=com.exemple.cdc.*")
                .append(",output=tcpserver,address=*")
                .append(",classdumpdir=/tmp/agent/source")
                .append(" ")
                .append("-javaagent:/exemple-cdc-agent.jar");

        return new CassandraContainer<>("cassandra:" + properties.getVersion())
                .withExposedPorts(9042, 6300)
                .withFileSystemBind(agent, "/exemple-cdc-agent.jar")
                .withFileSystemBind(lib, "/tmp/lib")
                .withConfigurationOverride("conf")
                .withEnv("JVM_EXTRA_OPTS", jvmExtraOpts.toString())
                .waitingFor(Wait.forLogMessage(".*Startup complete.*\\n", 1))
                .withLogConsumer(new Slf4jLogConsumer(LOG));
    }

}
