package com.exemple.cdc.agent.core.kafka;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;

import javax.inject.Singleton;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonNode;

import dagger.Module;
import dagger.Provides;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Module
@Slf4j
public class KafkaProducerModule {

    @Provides
    @Singleton
    public Producer<String, JsonNode> kafkaProducer() {

        var properties = kafkaProperties();

        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBoostrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);

        return new KafkaProducer<>(props);
    }

    @Provides
    @Singleton
    @SneakyThrows
    public KafkaProperties kafkaProperties() {

        var properties = new Yaml();
        Map<String, Object> values = properties.load(new FileInputStream("/tmp/conf/exemple-cdc.yml"));

        Map<String, Object> kafkaValues = (Map<String, Object>) values.get("kafka");

        LOG.info("Kafka Properties {}", kafkaValues);

        return KafkaProperties.builder()
                .boostrapServers((String) kafkaValues.get("bootstrap_servers"))
                .timeout((int) kafkaValues.get("timeout"))
                .topics((Map<String, String>) kafkaValues.get("topics"))
                .build();
    }

}
