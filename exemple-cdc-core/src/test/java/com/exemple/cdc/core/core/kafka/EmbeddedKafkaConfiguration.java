package com.exemple.cdc.core.core.kafka;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableConfigurationProperties(EmbeddedKafkaConfigurationProperties.class)
@RequiredArgsConstructor
@Slf4j
public class EmbeddedKafkaConfiguration {

    private final EmbeddedKafkaConfigurationProperties properties;

    @Bean
    public KafkaContainer embeddedKafka() {

        return new KafkaContainer(DockerImageName.parse("apache/kafka:" + properties.getVersion()))
                .withNetworkAliases("kafka_network")
                .withNetwork(Network.SHARED)
                .withExposedPorts(9092, 9093)
                .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
                .withLogConsumer(new Slf4jLogConsumer(LOG));
    }

}
