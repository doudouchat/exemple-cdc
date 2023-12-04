package com.exemple.cdc.agent.core.zookeeper;

import javax.inject.Singleton;

import org.apache.curator.framework.CuratorFramework;

import dagger.Component;

@Singleton
@Component(modules = ZookeeperClientModule.class)
public interface ZookeeperClientComponent {

    CuratorFramework zookeeperClient();

    ZookeeperProperties zookeeperProperties();

}
