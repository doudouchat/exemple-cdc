package com.exemple.cdc.core.configuration.kafka;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class KafkaProperties {

    private final String boostrapServers;

    private final int timeout;

    private final int socketConnectionTimeout;

    private final int socketConnectionTimeoutMax;

    private final Map<String, String> topics;

}
