package com.exemple.cdc.agent.core.kafka;

import javax.inject.Singleton;

import org.apache.kafka.clients.producer.Producer;

import com.fasterxml.jackson.databind.JsonNode;

import dagger.Component;

@Singleton
@Component(modules = KafkaProducerModule.class)
public interface KafkaProducerComponent {

    Producer<String, JsonNode> kafkaProducer();

    KafkaProperties kafkaProperties();

}
