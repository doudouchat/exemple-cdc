package com.exemple.cdc.core.configuration.event;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.curator.framework.CuratorFramework;
import org.apache.kafka.clients.producer.Producer;

import com.exemple.cdc.core.configuration.kafka.DaggerKafkaProducerComponent;
import com.exemple.cdc.core.configuration.kafka.KafkaProducerModule;
import com.exemple.cdc.core.configuration.kafka.KafkaProperties;
import com.exemple.cdc.core.configuration.zookeeper.DaggerZookeeperClientComponent;
import com.exemple.cdc.core.configuration.zookeeper.ZookeeperClientModule;
import com.exemple.cdc.core.configuration.zookeeper.ZookeeperProperties;
import com.exemple.cdc.core.event.EventProducer;

import dagger.Module;
import dagger.Provides;
import tools.jackson.databind.JsonNode;

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
