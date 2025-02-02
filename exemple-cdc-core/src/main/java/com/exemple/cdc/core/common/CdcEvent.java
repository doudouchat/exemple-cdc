package com.exemple.cdc.core.common;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

import org.apache.cassandra.schema.ColumnMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.exemple.cdc.core.common.PartitionKeyFactory.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Builder;
import lombok.Singular;

@Builder
public record CdcEvent(String key,
                       String resource,
                       JsonNode data,
                       @Singular Map<String, byte[]> headers,
                       OffsetDateTime date) {

    public static final String X_ORIGIN = "X_Origin";

    public static final String X_ORIGIN_VERSION = "X_Origin_Version";

    public static final String X_RESOURCE = "X_Resource";

    public static final String X_EVENT_TYPE = "X_Event_Type";

    public static final String X_USER = "X_User";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String id() {
        return date.toInstant().toEpochMilli() + "" + data.toString();
    }

    public ProducerRecord<String, JsonNode> createProducerRecord(String topic) {

        var productRecord = new ProducerRecord<String, JsonNode>(
                topic,
                null,
                date.toInstant().toEpochMilli(),
                key,
                data);
        headers.entrySet().forEach(header -> productRecord.headers().add(header.getKey(), header.getValue()));

        return productRecord;
    }

    public static class CdcEventBuilder {
        public CdcEventBuilder data(PartitionKey partitionKey) {
            ((ObjectNode) this.data).set(partitionKey.key(), MAPPER.convertValue(partitionKey.value(), JsonNode.class));
            return this;
        }

        public CdcEventBuilder data(JsonNode value) {
            this.data = value;
            return this;
        }

        public CdcEventBuilder data(ColumnMetadata column, JsonNode value) {
            ((ObjectNode) this.data).set(column.name.toCQLString(), value);
            return this;
        }

        public CdcEventBuilder resource(String value) {
            this.resource = value;
            this.header(CdcEvent.X_RESOURCE, this.resource.toUpperCase(Locale.getDefault()).getBytes(StandardCharsets.UTF_8));
            return this;
        }
    }

}
