package com.exemple.cdc.core.core.cassandra;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@ConfigurationProperties(prefix = "cassandra")
@RequiredArgsConstructor
@Getter
public class EmbeddedCassandraConfigurationProperties {

    private final String version;

    private final String agent;

    private final String lib;

    private final String conf;

}
