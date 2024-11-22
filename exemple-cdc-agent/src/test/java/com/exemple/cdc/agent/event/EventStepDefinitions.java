package com.exemple.cdc.agent.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fasterxml.jackson.databind.JsonNode;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventStepDefinitions {

    @Autowired
    private KafkaConsumer<String, JsonNode> consumerEvent;
    @Autowired
    private KafkaConsumer<String, JsonNode> consumerCountEvent;
    @Autowired
    private CqlSession session;

    @When("exec script")
    public void execScript(String script) {
        session.execute(script);
    }

    @Then("last message is")
    public void lastMessageIs(JsonNode body) {

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ConsumerRecords<String, JsonNode> records = consumerEvent.poll(Duration.ofSeconds(5));
            assertThat(records.iterator()).toIterable().last().satisfies(event -> {

                LOG.debug("received event {}:{}", event.key(), event.value().toPrettyString());
                assertThat(event.value()).isEqualTo(body);
            });
        });
    }

    @Then("receive {int} event(s)")
    public void countMessageIs(int count) {
        var counter = new AtomicInteger();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {

            ConsumerRecords<String, JsonNode> records = consumerCountEvent.poll(Duration.ofSeconds(1));
            counter.addAndGet(records.count());

            LOG.debug("count events {}", counter.intValue());
            assertThat(counter.intValue()).isGreaterThanOrEqualTo(count);
        });
        assertThat(counter.intValue()).isEqualTo(count);
    }

}
