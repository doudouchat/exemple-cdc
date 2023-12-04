package com.exemple.cdc.agent.core.zookeeper;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ZookeeperProperties {

    private final String host;
    private final int sessionTimeout;
    private final int connectionTimeout;
    private final int retry;
    private final int sleepMsBetweenRetries;
    private final long eventTTL;

}
