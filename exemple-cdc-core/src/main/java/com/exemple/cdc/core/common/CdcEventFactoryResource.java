package com.exemple.cdc.core.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.schema.ColumnMetadata;

import com.exemple.cdc.core.common.PartitionKeyFactory.PartitionKey;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.SneakyThrows;

public class CdcEventFactoryResource extends AbstractCdcEventFactory {

    @Override
    protected void readPartitionKey(PartitionKey partitionKey) {
        event.data(partitionKey);
    }

    @Override
    protected void readColumn(ColumnMetadata column, Row row) {
        switch (column.name.toCQLString()) {
            case "data":
                event.data(convertToJsonNode(row, column));
                break;
            case "user":
                event.header(CdcEvent.X_USER, convertToString(row, column).getBytes(StandardCharsets.UTF_8));
                break;
            case "version":
                event.header(CdcEvent.X_ORIGIN_VERSION, convertToString(row, column).getBytes(StandardCharsets.UTF_8));
                break;
            case "application":
                event.header(CdcEvent.X_ORIGIN, convertToString(row, column).getBytes(StandardCharsets.UTF_8));
                break;
            case "event_type":
                event.header(CdcEvent.X_EVENT_TYPE, convertToString(row, column).getBytes(StandardCharsets.UTF_8));
                break;
            default:
        }
    }

    @Override
    protected void readClusteringColumn(ColumnMetadata column, Object value) {
        var datetime = ((Date) value).getTime();
        event.date(OffsetDateTime.ofInstant(Instant.ofEpochMilli(datetime), ZoneId.systemDefault()));
    }

    @Override
    protected void readPartitionUpdate(PartitionUpdate modification) {
        event.resource(modification.metadata().name.replace("_event", ""));
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
