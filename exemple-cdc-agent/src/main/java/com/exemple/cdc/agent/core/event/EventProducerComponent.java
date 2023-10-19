package com.exemple.cdc.agent.core.event;

import javax.inject.Singleton;

import com.exemple.cdc.agent.event.EventProducer;

import dagger.Component;

@Singleton
@Component(modules = EventProducerModule.class)
public interface EventProducerComponent {

    EventProducer eventProducer();

}
