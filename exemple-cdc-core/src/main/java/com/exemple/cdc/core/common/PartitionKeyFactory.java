package com.exemple.cdc.core.common;

import java.nio.ByteBuffer;
import java.util.List;

import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.schema.ColumnMetadata;
import org.apache.cassandra.utils.ByteBufferUtil;

public class PartitionKeyFactory {

    private final List<ColumnMetadata> partitionKeyColumns;

    private final ByteBuffer partitionKeyValue;

    public PartitionKeyFactory(PartitionUpdate partitionUpdate) {
        this.partitionKeyColumns = partitionUpdate.metadata().partitionKeyColumns();
        this.partitionKeyValue = partitionUpdate.partitionKey().getKey().duplicate();
    }

    public boolean isCompositeKey() {
        return this.partitionKeyColumns.size() > 1;
    }

    public List<PartitionKey> getCompositeKey() {

        return partitionKeyColumns.stream()
                .map((ColumnMetadata column) -> {
                    var value = ByteBufferUtil.readBytesWithShortLength(partitionKeyValue);
                    partitionKeyValue.get();
                    return new PartitionKey(column, value);
                })
                .toList();
    }

    public PartitionKey getSimpleKey() {

        return partitionKeyColumns.stream()
                .map((ColumnMetadata column) -> new PartitionKey(column, partitionKeyValue))
                .findFirst()
                .orElseThrow();
    }

    static record PartitionKey(String key,
                               Object value) {

        PartitionKey(ColumnMetadata column, ByteBuffer value) {
            this(column.name.toCQLString(), column.type.compose(value));
        }
    }

}
