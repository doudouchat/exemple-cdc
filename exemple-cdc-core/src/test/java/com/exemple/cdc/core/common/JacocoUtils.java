package com.exemple.cdc.core.common;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.testcontainers.containers.GenericContainer;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class JacocoUtils {

    public static void copyJacocoExec(GenericContainer<?> container, int port, String target, String destinationPath) throws IOException {

        copyJacocoExec(container, port, target);
        container.copyFileFromContainer("/tmp/load/jacoco.exec", destinationPath);

    }
    
    public static void copyJacocoExec(GenericContainer<?> embeddedCassandra, int port, String target) throws IOException {

        try (var localJacocoFile = new FileOutputStream(target)) {

            try (var socket = new Socket(InetAddress.getByName(embeddedCassandra.getHost()), embeddedCassandra.getMappedPort(port))) {

                var writer = new RemoteControlWriter(socket.getOutputStream());
                writer.visitDumpCommand(true, false);

                var reader = new RemoteControlReader(socket.getInputStream());

                var localWriter = new ExecutionDataWriter(localJacocoFile);
                reader.setSessionInfoVisitor(localWriter);
                reader.setExecutionDataVisitor(localWriter);
                reader.read();

            }
        }
    }

}
