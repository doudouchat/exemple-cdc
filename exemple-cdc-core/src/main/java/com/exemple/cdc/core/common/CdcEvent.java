package com.exemple.cdc.core.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.schema.ColumnMetadata;

import com.exemple.cdc.core.common.PartitionKeyFactory.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
public class CdcEvent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String key;
    private final ObjectNode data;
    private final String resource;
    private final String eventType;
    private final String origin;
    private final String originVersion;
    private final OffsetDateTime date;

    public CdcEvent(Row row, PartitionUpdate modification) {

        data = row.columns().stream()
                .filter((ColumnMetadata column) -> "data".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToJsonNode(row, column))
                .map(ObjectNode.class::cast)
                .orElseGet(MAPPER::createObjectNode);

        var buildPartitionKey = new PartitionKeyFactory(modification);
        if (buildPartitionKey.isCompositeKey()) {
            key = buildPartitionKey.getCompositeKey().stream()
                    .mapMulti((PartitionKey partitionKey, Consumer<PartitionKey> build) -> {
                        data.set(partitionKey.key(), MAPPER.convertValue(partitionKey.value(), JsonNode.class));
                        build.accept(partitionKey);
                    })
                    .map(PartitionKey::value)
                    .map(String::valueOf)
                    .reduce((key1, key2) -> key1 + "." + key2)
                    .orElseThrow();

        } else {
            var partitionKey = buildPartitionKey.getSimpleKey();
            key = partitionKey.value().toString();
            data.set(partitionKey.key(), MAPPER.convertValue(partitionKey.value(), JsonNode.class));
        }

        date = modification.metadata().clusteringColumns().stream().findFirst()
                .map((ColumnMetadata column) -> {
                    var datetime = ((Date) column.type.compose(row.clustering().bufferAt(column.position()))).getTime();
                    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(datetime), ZoneId.systemDefault());
                }).orElseThrow();

        originVersion = row.columns().stream()
                .filter((ColumnMetadata column) -> "version".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToString(row, column))
                .orElse("");

        origin = row.columns().stream()
                .filter((ColumnMetadata column) -> "application".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToString(row, column))
                .orElse("");

        eventType = row.columns().stream()
                .filter((ColumnMetadata column) -> "event_type".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToString(row, column))
                .orElse("");

        resource = modification.metadata().name.replace("_event", "").toUpperCase(Locale.getDefault());

    }

    public String getId() {
        return date.toInstant().toEpochMilli() + "" + data.toString();
    }

    @SneakyThrows
    private static JsonNode convertToJsonNode(Row row, ColumnMetadata column) {
        var cell = row.getCell(column);
        return MAPPER.readTree(column.type.compose(cell.buffer()).toString());

    }

    private static String convertToString(Row row, ColumnMetadata column) {
        var cell = row.getCell(column);
        return column.type.compose(cell.buffer()).toString();

    }

}
