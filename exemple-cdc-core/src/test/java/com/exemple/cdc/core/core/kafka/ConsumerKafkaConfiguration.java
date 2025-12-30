package com.exemple.cdc.core.core.kafka;

import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.testcontainers.kafka.KafkaContainer;

import tools.jackson.databind.JsonNode;

@Configuration
public class ConsumerKafkaConfiguration {

    @Autowired
    private KafkaContainer embeddedKafka;

    @Bean
    public <T> KafkaConsumer<String, T> consumerEvent() {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + embeddedKafka.getMappedPort(9092));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, JsonNode.class);
        return new KafkaConsumer<>(props);
    }

}
