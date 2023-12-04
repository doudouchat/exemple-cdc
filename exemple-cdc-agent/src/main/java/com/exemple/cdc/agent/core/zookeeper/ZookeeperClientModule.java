package com.exemple.cdc.agent.core.zookeeper;

import java.io.FileInputStream;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.yaml.snakeyaml.Yaml;

import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Module
@Slf4j
public class ZookeeperClientModule {

    private final Map<String, Object> zookeeperProperties;

    @Inject
    @SneakyThrows
    public ZookeeperClientModule(String path) {
        Map<String, Object> properties = new Yaml().load(new FileInputStream(path));

        this.zookeeperProperties = (Map<String, Object>) properties.get("zookeeper");

        LOG.info("Zookeeper Properties {}", zookeeperProperties);

    }

    public CuratorFramework zookeeperCuratorFramework() {

        var properties = zookeeperProperties();

        var client = CuratorFrameworkFactory.newClient(
                properties.getHost(),
                properties.getSessionTimeout(),
                properties.getConnectionTimeout(),
                new RetryNTimes(properties.getRetry(), properties.getSleepMsBetweenRetries()));

        client.getConnectionStateListenable().addListener((c, state) -> LOG.debug("State changed to: {}", state));

        return client;
    }

    @Provides
    @Singleton
    public CuratorFramework zookeeperClient() {

        var curatorClient = zookeeperCuratorFramework();
        curatorClient.start();

        return curatorClient.usingNamespace("event");

    }

    @Provides
    @Singleton
    public ZookeeperProperties zookeeperProperties() {

        return ZookeeperProperties.builder()
                .host((String) zookeeperProperties.get("host"))
                .connectionTimeout((int) zookeeperProperties.get("connection_timeout"))
                .sessionTimeout((int) zookeeperProperties.get("session_timeout"))
                .retry((int) zookeeperProperties.get("retry"))
                .eventTTL((int) zookeeperProperties.get("eventTTL"))
                .build();
    }

}
