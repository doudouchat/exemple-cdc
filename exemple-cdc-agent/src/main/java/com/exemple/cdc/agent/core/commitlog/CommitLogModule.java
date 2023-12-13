package com.exemple.cdc.agent.core.commitlog;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.cassandra.db.commitlog.CommitLogReadHandler;
import org.apache.cassandra.db.commitlog.CommitLogReader;

import com.exemple.cdc.agent.commitlog.CommitLogReadHandlerImpl;
import com.exemple.cdc.agent.commitlog.CommitLogReaderImpl;
import com.exemple.cdc.agent.event.EventProducer;

import dagger.Module;
import dagger.Provides;

@Module
public class CommitLogModule {

    private final EventProducer eventProducer;

    @Inject
    public CommitLogModule(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Provides
    @Singleton
    public CommitLogReader commitLogReader() {
        return new CommitLogReaderImpl();
    }

    @Provides
    @Singleton
    public CommitLogReadHandler commitLogReadHandler() {
        return new CommitLogReadHandlerImpl(eventProducer);
    }

}
