package com.exemple.cdc.agent.core;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.CassandraContainer;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.exemple.cdc.agent.core.cassandra.EmbeddedCassandraConfiguration;
import com.exemple.cdc.agent.core.kafka.ConsumerKafkaConfiguration;

import jakarta.annotation.PostConstruct;

@Configuration
@ComponentScan(basePackages = "com.exemple.cdc.agent")
@Import({ EmbeddedCassandraConfiguration.class, ConsumerKafkaConfiguration.class })
public class AgentTestConfiguration {

    @Autowired
    private CassandraContainer<?> cassandraContainer;

    @Autowired
    private KafkaConsumer<?, ?> consumerEvent;

    @Bean
    public CqlSession session() throws IOException {

        var cassandraResource = ResourceUtils.getFile("classpath:cassandra.conf");

        var loader = DriverConfigLoader.fromFile(cassandraResource);

        return CqlSession.builder()
                .withConfigLoader(loader)
                .addContactPoint(cassandraContainer.getContactPoint())
                .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                .build();
    }

    @PostConstruct
    public void suscribeConsumerEvent() {

        consumerEvent.subscribe(List.of("topic_test").stream().distinct().toList(), new ConsumerRebalanceListener() {

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                consumerEvent.seekToBeginning(partitions);
            }
        });
    }

}
