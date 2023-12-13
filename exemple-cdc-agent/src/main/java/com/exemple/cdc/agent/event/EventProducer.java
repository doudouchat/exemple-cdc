package com.exemple.cdc.agent.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.nodes.PersistentNode;
import org.apache.curator.framework.recipes.nodes.PersistentTtlNode;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.zookeeper.CreateMode;

import com.exemple.cdc.agent.common.CdcEvent;
import com.exemple.cdc.agent.core.kafka.KafkaProperties;
import com.exemple.cdc.agent.core.zookeeper.ZookeeperProperties;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class EventProducer {

    public static final String X_ORIGIN = "X_Origin";

    public static final String X_ORIGIN_VERSION = "X_Origin_Version";

    public static final String X_RESOURCE = "X_Resource";

    public static final String X_EVENT_TYPE = "X_Event_Type";

    private final Producer<String, JsonNode> kafkaProducer;

    private final KafkaProperties kafkaProperties;

    private final CuratorFramework zookeeperClient;

    private final ZookeeperProperties zookeeperProperties;

    @SneakyThrows
    public void send(CdcEvent event) {

        try (PersistentTtlNode node = createEvent(event)) {

            InterProcessLock lock = new InterProcessSemaphoreMutex(zookeeperClient, "/" + event.getResource() + event.getId());

            try {
                lock.acquire();

                var status = new String(zookeeperClient.getData().forPath("/" + event.getResource() + "/" + event.getId()), StandardCharsets.UTF_8);

                if (!"COMPLETED".equals(status)) {

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

                    zookeeperClient.setData().forPath("/" + event.getResource() + "/" + event.getId(), "COMPLETED".getBytes(StandardCharsets.UTF_8));

                }

            } finally {
                lock.release();
            }
        }

    }

    private PersistentTtlNode createEvent(CdcEvent event) throws Exception {

        if (zookeeperClient.checkExists().creatingParentsIfNeeded().forPath("/" + event.getResource()) == null) {
            (new PersistentNode(zookeeperClient, CreateMode.PERSISTENT, false, "/" + event.getResource(), new byte[0])).start();
        }

        if (zookeeperClient.checkExists().forPath("/" + event.getResource() + "/" + event.getId()) == null) {
            var node = new PersistentTtlNode(zookeeperClient, "/" + event.getResource() + "/" + event.getId(), zookeeperProperties.getEventTTL(),
                    new byte[0]);
            node.start();
            return node;
        }

        return null;
    }

}
