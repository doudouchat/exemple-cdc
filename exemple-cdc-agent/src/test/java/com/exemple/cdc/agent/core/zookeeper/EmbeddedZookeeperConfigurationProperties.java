package com.exemple.cdc.agent.core.zookeeper;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@ConfigurationProperties(prefix = "zookeeper")
@RequiredArgsConstructor
@Getter
public class EmbeddedZookeeperConfigurationProperties {

    private final String version;
}
