package com.exemple.cdc.core.configuration.kafka;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;
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

    private final Map<String, Object> kafkaProperties;

    @Inject
    @SneakyThrows
    public KafkaProducerModule(String path) {
        Map<String, Object> properties = new Yaml().load(new FileInputStream(path));

        this.kafkaProperties = (Map<String, Object>) properties.get("kafka");

        LOG.info("Kafka Properties {}", kafkaProperties);

    }

    @Provides
    @Singleton
    public Producer<String, JsonNode> kafkaProducer() {

        var properties = kafkaProperties();

        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBoostrapServers());
        props.put(ProducerConfig.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG, 60_000);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);

        return new KafkaProducer<>(props);
    }

    @Provides
    @Singleton
    public KafkaProperties kafkaProperties() {

        return KafkaProperties.builder()
                .boostrapServers((String) kafkaProperties.get("bootstrap_servers"))
                .timeout((int) kafkaProperties.get("timeout"))
                .topics((Map<String, String>) kafkaProperties.get("topics"))
                .build();
    }

}
