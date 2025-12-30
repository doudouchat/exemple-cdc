package com.exemple.cdc.core.event;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.nodes.PersistentNode;
import org.apache.curator.framework.recipes.nodes.PersistentTtlNode;
import org.apache.kafka.clients.producer.Producer;
import org.apache.zookeeper.CreateMode;

import com.exemple.cdc.core.common.CdcEvent;
import com.exemple.cdc.core.configuration.kafka.KafkaProperties;
import com.exemple.cdc.core.configuration.zookeeper.ZookeeperProperties;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@RequiredArgsConstructor
@Slf4j
public class EventProducer {

    private final Producer<String, JsonNode> kafkaProducer;

    private final KafkaProperties kafkaProperties;

    private final CuratorFramework zookeeperClient;

    private final ZookeeperProperties zookeeperProperties;

    @SneakyThrows
    public void send(CdcEvent event) {

        try (PersistentTtlNode node = createEvent(event)) {

            InterProcessLock lock = new InterProcessSemaphoreMutex(zookeeperClient, "/" + event.resource() + "/" + event.id());

            try {
                lock.acquire();

                var status = new String(zookeeperClient.getData().forPath("/" + event.resource() + "/" + event.id()), StandardCharsets.UTF_8);

                if (!"COMPLETED".equals(status)) {

                    var topic = kafkaProperties.getTopics().computeIfAbsent(event.resource(), (String absentResource) -> {
                        throw new IllegalStateException(absentResource + " has not any topic");
                    });

                    var productRecord = event.createProducerRecord(topic);

                    LOG.trace("Send event {}", productRecord);

                    kafkaProducer.send(productRecord).get(kafkaProperties.getTimeout(), TimeUnit.SECONDS);

                    zookeeperClient.setData().forPath("/" + event.resource() + "/" + event.id(), "COMPLETED".getBytes(StandardCharsets.UTF_8));

                }

            } finally {
                lock.release();
            }
        }

    }

    private PersistentTtlNode createEvent(CdcEvent event) throws Exception {

        if (zookeeperClient.checkExists().creatingParentsIfNeeded().forPath("/" + event.resource()) == null) {
            (new PersistentNode(zookeeperClient, CreateMode.PERSISTENT, false, "/" + event.resource(), new byte[0])).start();
        }

        if (zookeeperClient.checkExists().forPath("/" + event.resource() + "/" + event.id()) == null) {
            var node = new PersistentTtlNode(zookeeperClient, "/" + event.resource() + "/" + event.id(), zookeeperProperties.getEventTTL(),
                    new byte[0]);
            node.start();
            return node;
        }

        return null;
    }

}
