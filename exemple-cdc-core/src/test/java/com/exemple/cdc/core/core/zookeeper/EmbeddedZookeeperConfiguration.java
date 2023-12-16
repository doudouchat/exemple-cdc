package com.exemple.cdc.core.core.zookeeper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(EmbeddedZookeeperConfigurationProperties.class)
@Testcontainers
@RequiredArgsConstructor
public class EmbeddedZookeeperConfiguration {

    private final EmbeddedZookeeperConfigurationProperties properties;

    @Bean
    @ServiceConnection
    public GenericContainer embeddedZookeeper() {

        return new GenericContainer<>("zookeeper:" + properties.getVersion())
                .withNetworkAliases("zookeeper_network")
                .withNetwork(Network.SHARED)
                .withExposedPorts(2181);
    }

}
