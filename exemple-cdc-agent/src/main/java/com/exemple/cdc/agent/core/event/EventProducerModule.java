package com.exemple.cdc.agent.core.event;

import javax.inject.Singleton;

import com.exemple.cdc.agent.event.EventProducer;

import dagger.Module;
import dagger.Provides;

@Module
public class EventProducerModule {

    @Provides
    @Singleton
    public EventProducer eventProducer() {

        return new EventProducer();
    }

}
