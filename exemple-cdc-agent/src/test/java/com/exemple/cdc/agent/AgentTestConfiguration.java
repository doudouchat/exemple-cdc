package com.exemple.cdc.agent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

import jakarta.annotation.PostConstruct;

@Configuration
@ComponentScan(basePackages = "com.exemple.cdc.agent")
public class AgentTestConfiguration {

    @Autowired
    private KafkaConsumer<?, ?> consumerEvent;

    @Autowired
    private KafkaConsumer<?, ?> consumerCountEvent;

    @Value("${cassandra.port}")
    private int port;

    @Bean
    public CqlSession session() throws IOException {

        var cassandraResource = ResourceUtils.getFile("classpath:cassandra.conf");

        var loader = DriverConfigLoader.fromFile(cassandraResource);

        return CqlSession.builder()
                .withConfigLoader(loader)
                .addContactPoint(InetSocketAddress.createUnresolved("localhost", port))
                .withLocalDatacenter("datacenter1")
                .build();
    }

    @PostConstruct
    public void suscribeConsumerEvent() {

        consumerEvent.subscribe(List.of("topic_test").stream().distinct().toList(), new ConsumerRebalanceListener() {

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // NOP
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                consumerEvent.seekToBeginning(partitions);
            }
        });

        consumerCountEvent.subscribe(List.of("topic_test").stream().distinct().toList(), new ConsumerRebalanceListener() {

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // NOP
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                consumerCountEvent.seekToBeginning(partitions);
            }
        });
    }

}
