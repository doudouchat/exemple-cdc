package com.exemple.cdc.core.commitlog;

import org.apache.cassandra.db.LivenessInfo;
import org.apache.cassandra.db.Mutation;
import org.apache.cassandra.db.commitlog.CommitLogDescriptor;
import org.apache.cassandra.db.commitlog.CommitLogReadHandler;
import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.Row;

import com.exemple.cdc.core.common.CdcEventFactoryResource;
import com.exemple.cdc.core.event.EventProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CommitLogReadHandlerImpl implements CommitLogReadHandler {

    private final EventProducer eventProducer;

    @Override
    public boolean shouldSkipSegmentOnError(CommitLogReadException exception) {
        return true;
    }

    @Override
    public void handleUnrecoverableError(CommitLogReadException exception) {
        LOG.error("Unrecoverable error when reading commit log", exception);

    }

    @Override
    public void handleMutation(Mutation mutation, int size, int entryLocation, CommitLogDescriptor descriptor) {

        mutation.getPartitionUpdates().stream()
                .filter((PartitionUpdate modification) -> modification.metadata().params.cdc)
                .forEach(this::process);

    }

    private void process(PartitionUpdate modification) {

        var it = modification.unfilteredIterator();

        while (it.hasNext()) {
            var rowOrRangeTombstone = it.next();
            var row = (Row) rowOrRangeTombstone;

            if (!isInsert(row)) {
                LOG.error("Only Insert is expected");
                continue;
            }
            var event = new CdcEventFactoryResource().build(row, modification);

            LOG.trace("process {} {}", modification.metadata(), row);
            eventProducer.send(event);

        }

    }

    private static boolean isInsert(Row row) {
        return row.primaryKeyLivenessInfo().timestamp() > LivenessInfo.NO_TIMESTAMP;
    }

}
