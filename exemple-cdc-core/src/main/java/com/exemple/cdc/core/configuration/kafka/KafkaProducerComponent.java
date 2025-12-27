package com.exemple.cdc.core.configuration.kafka;

import javax.inject.Singleton;

import org.apache.kafka.clients.producer.Producer;

import dagger.Component;
import tools.jackson.databind.JsonNode;

@Singleton
@Component(modules = KafkaProducerModule.class)
public interface KafkaProducerComponent {

    Producer<String, JsonNode> kafkaProducer();

    KafkaProperties kafkaProperties();

}
