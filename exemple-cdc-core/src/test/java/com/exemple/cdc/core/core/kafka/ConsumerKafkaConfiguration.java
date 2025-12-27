package com.exemple.cdc.core.core.kafka;

import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJson3Deserializer.class);
        props.put(KafkaJsonDeserializerConfig.JSON_VALUE_TYPE, JsonNode.class.getName());
        return new KafkaConsumer<>(props);
    }

    public static class KafkaJson3Deserializer extends KafkaJsonDeserializer<JsonNode> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public JsonNode deserialize(String ignored, byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            return MAPPER.readValue(bytes, JsonNode.class);
        }

    }

}
