package com.exemple.cdc.core.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.apache.cassandra.db.context.CounterContext;
import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.schema.ColumnMetadata;

import com.exemple.cdc.core.common.PartitionKeyFactory.PartitionKey;

import tools.jackson.databind.node.LongNode;

public class CdcEventFactoryCounter extends AbstractCdcEventFactory {

    @Override
    protected void readPartitionKey(PartitionKey partitionKey) {
        event.data(partitionKey);
    }

    @Override
    protected void readColumn(ColumnMetadata column, Row row) {
        event.data(column, new LongNode(CounterContext.instance().total(row.getCell(column))));
    }

    @Override
    protected void readClusteringColumn(ColumnMetadata column, Object value) {
        switch (column.name.toCQLString()) {
            case "date":
                var datetime = ((Date) value).getTime();
                event.date(OffsetDateTime.ofInstant(Instant.ofEpochMilli(datetime), ZoneId.systemDefault()));
                break;
            case "user":
                event.header(CdcEvent.X_USER, ((String) value).getBytes(StandardCharsets.UTF_8));
                break;
            case "application":
                event.header(CdcEvent.X_ORIGIN, ((String) value).getBytes(StandardCharsets.UTF_8));
                break;
            default:
        }
    }

    @Override
    protected void readPartitionUpdate(PartitionUpdate modification) {
        event.resource(modification.metadata().name.replace("_history", ""));
    }
}
