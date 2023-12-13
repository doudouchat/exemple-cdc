package com.exemple.cdc.agent.core.kafka;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class KafkaProperties {

    private final String boostrapServers;

    private final int timeout;

    private final Map<String, String> topics;

}
