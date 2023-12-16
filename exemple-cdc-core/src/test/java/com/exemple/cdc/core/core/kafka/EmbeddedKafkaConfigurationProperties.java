package com.exemple.cdc.core.core.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@ConfigurationProperties(prefix = "kafka")
@RequiredArgsConstructor
@Getter
public class EmbeddedKafkaConfigurationProperties {

    private final String version;
}
