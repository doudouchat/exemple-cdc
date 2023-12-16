package com.exemple.cdc.core.configuration.event;

import javax.inject.Singleton;

import com.exemple.cdc.core.event.EventProducer;

import dagger.Component;

@Singleton
@Component(modules = EventProducerModule.class)
public interface EventProducerComponent {

    EventProducer eventProducer();

}
