package com.exemple.cdc.agent.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;

import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.schema.ColumnMetadata;

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

    private final ObjectNode data;
    private final String resource;
    private final String eventType;
    private final String origin;
    private final String originVersion;
    private final OffsetDateTime date;

    public CdcEvent(Row row, PartitionUpdate modification) {

        data = modification.metadata().columns().stream()
                .filter((ColumnMetadata column) -> "data".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToJsonNode(row, column))
                .map(ObjectNode.class::cast)
                .orElseThrow();

        modification.metadata().partitionKeyColumns().forEach((ColumnMetadata column) -> data.set(column.name.toCQLString(),
                MAPPER.convertValue(column.type.compose(modification.partitionKey().getKey()), JsonNode.class)));

        date = modification.metadata().clusteringColumns().stream().findFirst()
                .map((ColumnMetadata column) -> {
                    var datetime = ((Date) column.type.compose(row.clustering().bufferAt(column.position()))).getTime();
                    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(datetime), ZoneId.systemDefault());
                }).orElseThrow();

        originVersion = modification.metadata().columns().stream()
                .filter((ColumnMetadata column) -> "version".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToString(row, column))
                .orElseThrow();

        origin = modification.metadata().columns().stream()
                .filter((ColumnMetadata column) -> "application".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToString(row, column))
                .orElseThrow();

        eventType = modification.metadata().columns().stream()
                .filter((ColumnMetadata column) -> "event_type".equals(column.name.toCQLString()))
                .findFirst()
                .map((ColumnMetadata column) -> convertToString(row, column))
                .orElseThrow();

        resource = modification.metadata().name.replace("_event", "").toUpperCase(Locale.getDefault());

    }

    @SneakyThrows
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
