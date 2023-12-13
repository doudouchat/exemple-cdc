package com.exemple.cdc.agent.core.event;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.curator.framework.CuratorFramework;
import org.apache.kafka.clients.producer.Producer;

import com.exemple.cdc.agent.core.kafka.DaggerKafkaProducerComponent;
import com.exemple.cdc.agent.core.kafka.KafkaProducerModule;
import com.exemple.cdc.agent.core.kafka.KafkaProperties;
import com.exemple.cdc.agent.core.zookeeper.DaggerZookeeperClientComponent;
import com.exemple.cdc.agent.core.zookeeper.ZookeeperClientModule;
import com.exemple.cdc.agent.core.zookeeper.ZookeeperProperties;
import com.exemple.cdc.agent.event.EventProducer;
import com.fasterxml.jackson.databind.JsonNode;

import dagger.Module;
import dagger.Provides;

@Module
public class EventProducerModule {

    private final Producer<String, JsonNode> kafkaProducer;

    private final CuratorFramework zookeeperClient;

    private final KafkaProperties kafkaProperties;

    private final ZookeeperProperties zookeeperProperties;

    @Inject
    public EventProducerModule(String path) {
        var kafkaProducerComponent = DaggerKafkaProducerComponent.builder().kafkaProducerModule(new KafkaProducerModule(path)).build();
        this.kafkaProducer = kafkaProducerComponent.kafkaProducer();
        this.kafkaProperties = kafkaProducerComponent.kafkaProperties();

        var zookeeperClientComponent = DaggerZookeeperClientComponent.builder().zookeeperClientModule(new ZookeeperClientModule(path)).build();
        this.zookeeperClient = zookeeperClientComponent.zookeeperClient();
        this.zookeeperProperties = zookeeperClientComponent.zookeeperProperties();
    }

    @Provides
    @Singleton
    public EventProducer eventProducer() {
        return new EventProducer(kafkaProducer, kafkaProperties, zookeeperClient, zookeeperProperties);
    }

}
