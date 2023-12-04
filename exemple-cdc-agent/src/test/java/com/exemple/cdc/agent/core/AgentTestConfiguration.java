package com.exemple.cdc.agent.core;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.CassandraContainer;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.exemple.cdc.agent.core.kafka.ConsumerKafkaConfiguration;
import com.exemple.cdc.agent.core.kafka.EmbeddedKafkaConfiguration;
import com.exemple.cdc.agent.core.zookeeper.EmbeddedZookeeperConfiguration;

import jakarta.annotation.PostConstruct;

@Configuration
@Import({ EmbeddedKafkaConfiguration.class, ConsumerKafkaConfiguration.class, EmbeddedZookeeperConfiguration.class })
public class AgentTestConfiguration {

    @Autowired
    private KafkaConsumer<?, ?> consumerEvent;

    @Bean
    @ConditionalOnBean(CassandraContainer.class)
    public CqlSession session(CassandraContainer embeddedCassandra) throws IOException {

        var cassandraResource = ResourceUtils.getFile("classpath:cassandra.conf");

        var loader = DriverConfigLoader.fromFile(cassandraResource);

        return CqlSession.builder()
                .withConfigLoader(loader)
                .addContactPoint(embeddedCassandra.getContactPoint())
                .withLocalDatacenter(embeddedCassandra.getLocalDatacenter())
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
