package com.exemple.cdc.core.configuration.commitlog;

import javax.inject.Singleton;

import org.apache.cassandra.db.commitlog.CommitLogReadHandler;
import org.apache.cassandra.db.commitlog.CommitLogReader;

import dagger.Component;

@Singleton
@Component(modules = CommitLogModule.class)
public interface CommitLogComponent {

    CommitLogReader commitLogReader();

    CommitLogReadHandler commitLogReadHandler();

}
