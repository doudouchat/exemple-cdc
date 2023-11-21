package com.exemple.cdc.agent.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.exemple.cdc.agent.common.CdcEvent;
import com.exemple.cdc.agent.core.kafka.DaggerKafkaProducerComponent;
import com.exemple.cdc.agent.core.kafka.KafkaProducerModule;
import com.exemple.cdc.agent.core.kafka.KafkaProperties;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.SneakyThrows;

public class EventProducer {

    public static final String X_ORIGIN = "X_Origin";

    public static final String X_ORIGIN_VERSION = "X_Origin_Version";

    public static final String X_RESOURCE = "X_Resource";

    public static final String X_EVENT_TYPE = "X_Event_Type";

    private final Producer<String, JsonNode> kafkaProducer;

    private final KafkaProperties kafkaProperties;

    public EventProducer(String path) {
        var component = DaggerKafkaProducerComponent.builder().kafkaProducerModule(new KafkaProducerModule(path)).build();
        this.kafkaProducer = component.kafkaProducer();
        this.kafkaProperties = component.kafkaProperties();
    }

    public EventProducer() {
        this("/tmp/conf/exemple-cdc.yml");
    }

    @SneakyThrows
    public void send(CdcEvent event) {

        var resource = event.getResource();
        var topic = kafkaProperties.getTopics().computeIfAbsent(resource.toLowerCase(), (String absentResource) -> {
            throw new IllegalStateException(absentResource + " has not any topic");
        });
        var data = event.getData();

        var productRecord = new ProducerRecord<String, JsonNode>(
                topic,
                null,
                event.getDate().toInstant().toEpochMilli(),
                UUID.randomUUID().toString(),
                data);
        productRecord.headers()
                .add(X_RESOURCE, resource.getBytes(StandardCharsets.UTF_8))
                .add(X_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8))
                .add(X_ORIGIN, event.getOrigin().getBytes(StandardCharsets.UTF_8))
                .add(X_ORIGIN_VERSION, event.getOriginVersion().getBytes(StandardCharsets.UTF_8));

        kafkaProducer.send(productRecord).get(kafkaProperties.getTimeout(), TimeUnit.SECONDS);

    }

}
