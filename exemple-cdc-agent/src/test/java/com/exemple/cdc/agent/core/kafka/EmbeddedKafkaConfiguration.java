package com.exemple.cdc.agent.core.kafka;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(EmbeddedKafkaConfigurationProperties.class)
@Testcontainers
@Slf4j
@RequiredArgsConstructor
public class EmbeddedKafkaConfiguration {

    private final EmbeddedKafkaConfigurationProperties properties;

    @Bean
    @ServiceConnection
    public KafkaContainer embeddedKafka() throws IOException {

        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:" + properties.getVersion()))
                .withNetworkAliases("kafka_network")
                .withNetwork(Network.SHARED)
                .withExposedPorts(9092, 9093)
                .withLogConsumer(new Slf4jLogConsumer(LOG));
    }

}
