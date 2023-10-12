package com.exemple.cdc.agent.core;

import java.io.FileNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.CassandraContainer;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.exemple.cdc.agent.core.cassandra.EmbeddedCassandraConfiguration;

@Configuration
@ComponentScan(basePackages = "com.exemple.cdc.agent")
@Import(EmbeddedCassandraConfiguration.class)
public class AgentTestConfiguration {

    @Autowired
    private CassandraContainer<?> cassandraContainer;

    @Bean
    public CqlSession session() throws FileNotFoundException {

        var cassandraResource = ResourceUtils.getFile("classpath:cassandra.conf");

        var loader = DriverConfigLoader.fromFile(cassandraResource);

        return CqlSession.builder()
                .withConfigLoader(loader)
                .addContactPoint(cassandraContainer.getContactPoint())
                .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                .build();
    }

}
