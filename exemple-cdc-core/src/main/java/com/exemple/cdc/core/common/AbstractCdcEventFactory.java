package com.exemple.cdc.core.common;

import java.util.function.Consumer;

import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.schema.ColumnMetadata;

import com.exemple.cdc.core.common.PartitionKeyFactory.PartitionKey;

import tools.jackson.databind.ObjectMapper;

public abstract class AbstractCdcEventFactory {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final CdcEvent.CdcEventBuilder event;

    protected AbstractCdcEventFactory() {
        this.event = CdcEvent.builder().data(MAPPER.createObjectNode());
    }

    public final CdcEvent build(Row row, PartitionUpdate modification) {

        row.columns().forEach((ColumnMetadata column) -> readColumn(column, row));

        var buildPartitionKey = new PartitionKeyFactory(modification);
        if (buildPartitionKey.isCompositeKey()) {
            event.key(buildPartitionKey.getCompositeKey().stream()
                    .mapMulti((PartitionKey partitionKey, Consumer<PartitionKey> build) -> {
                        readPartitionKey(partitionKey);
                        build.accept(partitionKey);
                    })
                    .map(PartitionKey::value)
                    .map(String::valueOf)
                    .reduce((key1, key2) -> key1 + "." + key2)
                    .orElseThrow());

        } else {
            var partitionKey = buildPartitionKey.getSimpleKey();
            event.key(partitionKey.value().toString());
            readPartitionKey(partitionKey);
        }

        modification.metadata().clusteringColumns().stream()
                .forEach((ColumnMetadata column) -> {
                    var value = column.type.compose(row.clustering().bufferAt(column.position()));
                    readClusteringColumn(column, value);
                });

        readPartitionUpdate(modification);

        return event.build();

    }

    protected abstract void readPartitionKey(PartitionKey partitionKey);

    protected abstract void readColumn(ColumnMetadata column, Row row);

    protected abstract void readClusteringColumn(ColumnMetadata column, Object value);

    protected abstract void readPartitionUpdate(PartitionUpdate modification);

}
