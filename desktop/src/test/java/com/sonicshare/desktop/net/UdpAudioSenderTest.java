package com.sonicshare.desktop.net;

import com.sonicshare.desktop.audio.AudioFormatConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class UdpAudioSenderTest {

    private UdpAudioSender sender;
    private final int testPort = 50099;

    @BeforeEach
    public void setup() throws Exception {
        sender = new UdpAudioSender(testPort);
        sender.startListening();
    }

    @AfterEach
    public void teardown() {
        if (sender != null) {
            sender.stopListening();
        }
    }

    @Test
    public void testConnectAndDisconnectHandshake() throws Exception {
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch disconnectLatch = new CountDownLatch(1);

        sender.setConnectionListener(new UdpAudioSender.ConnectionListener() {
            @Override
            public void onConnected(InetAddress client, int port) {
                connectLatch.countDown();
            }

            @Override
            public void onDisconnected() {
                disconnectLatch.countDown();
            }
        });

        try (DatagramSocket clientSocket = new DatagramSocket()) {
            // Send CONNECT
            byte[] connectData = AudioFormatConfig.CMD_CONNECT.getBytes(StandardCharsets.UTF_8);
            DatagramPacket connectPacket = new DatagramPacket(
                connectData, connectData.length, InetAddress.getByName("127.0.0.1"), testPort
            );
            clientSocket.send(connectPacket);

            assertTrue(connectLatch.await(2, TimeUnit.SECONDS), "Client should connect");
            assertTrue(sender.isConnected(), "Sender state should be connected");
            assertNotNull(sender.getClientAddress(), "Client address should be set");
            assertEquals(clientSocket.getLocalPort(), sender.getClientPort(), "Client port should match");

            // Send DISCONNECT
            byte[] disconnectData = AudioFormatConfig.CMD_DISCONNECT.getBytes(StandardCharsets.UTF_8);
            DatagramPacket disconnectPacket = new DatagramPacket(
                disconnectData, disconnectData.length, InetAddress.getByName("127.0.0.1"), testPort
            );
            clientSocket.send(disconnectPacket);

            assertTrue(disconnectLatch.await(2, TimeUnit.SECONDS), "Client should disconnect");
            assertFalse(sender.isConnected(), "Sender state should be disconnected");
        }
    }

    @Test
    public void testSendChunkWhenConnected() throws Exception {
        CountDownLatch connectLatch = new CountDownLatch(1);

        sender.setConnectionListener(new UdpAudioSender.ConnectionListener() {
            @Override
            public void onConnected(InetAddress client, int port) {
                connectLatch.countDown();
            }

            @Override
            public void onDisconnected() {
            }
        });

        try (DatagramSocket clientSocket = new DatagramSocket()) {
            clientSocket.setSoTimeout(2000);

            // Send CONNECT
            byte[] connectData = AudioFormatConfig.CMD_CONNECT.getBytes(StandardCharsets.UTF_8);
            DatagramPacket connectPacket = new DatagramPacket(
                connectData, connectData.length, InetAddress.getByName("127.0.0.1"), testPort
            );
            clientSocket.send(connectPacket);

            assertTrue(connectLatch.await(2, TimeUnit.SECONDS), "Client should connect");

            // Send audio chunk
            byte[] chunk = new byte[]{1, 2, 3, 4, 5};
            sender.sendChunk(chunk, chunk.length);

            byte[] recvBuffer = new byte[16];
            DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
            clientSocket.receive(recvPacket);

            assertEquals(chunk.length, recvPacket.getLength());
            for (int i = 0; i < chunk.length; i++) {
                assertEquals(chunk[i], recvPacket.getData()[i]);
            }
        }
    }
}
