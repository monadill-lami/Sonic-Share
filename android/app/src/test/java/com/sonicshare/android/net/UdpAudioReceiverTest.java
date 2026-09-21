package com.sonicshare.android.net;

import com.sonicshare.android.audio.AudioPlaybackEngine;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class UdpAudioReceiverTest {

    static class TestPlaybackEngine extends AudioPlaybackEngine {
        private volatile boolean playing = false;
        private final AtomicInteger totalBytesWritten = new AtomicInteger(0);

        @Override
        public synchronized void start() {
            playing = true;
        }

        @Override
        public int writeAudio(byte[] data, int offset, int length) {
            if (!playing || data == null) return 0;
            totalBytesWritten.addAndGet(length);
            return length;
        }

        @Override
        public synchronized void stop() {
            playing = false;
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }

        public int getTotalBytesWritten() {
            return totalBytesWritten.get();
        }
    }

    @Test
    public void testCalculateRmsSilence() {
        byte[] silence = new byte[2048];
        float level = UdpAudioReceiver.calculateRms(silence, silence.length);
        assertEquals(0.0f, level, 0.0001f);
    }

    @Test
    public void testCalculateRmsModerateTone() {
        // 7500 amplitude in 16-bit little endian: 7500 = 0x1D4C -> low=0x4C, high=0x1D
        byte[] buffer = new byte[2048];
        for (int i = 0; i < buffer.length; i += 2) {
            buffer[i] = 0x4C;
            buffer[i + 1] = 0x1D;
        }
        float level = UdpAudioReceiver.calculateRms(buffer, buffer.length);
        // RMS = 7500, level = 7500 / 15000 = 0.5
        assertEquals(0.5f, level, 0.01f);
    }

    @Test
    public void testCalculateRmsClampedAtMax() {
        // 30000 amplitude: 30000 / 15000 = 2.0 -> clamped to 1.0
        byte[] buffer = new byte[2048];
        for (int i = 0; i < buffer.length; i += 2) {
            short val = 30000;
            buffer[i] = (byte) (val & 0xFF);
            buffer[i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        float level = UdpAudioReceiver.calculateRms(buffer, buffer.length);
        assertEquals(1.0f, level, 0.0001f);
    }

    @Test
    public void testCalculateRmsEmptyOrInvalid() {
        assertEquals(0.0f, UdpAudioReceiver.calculateRms(null, 100), 0.0001f);
        assertEquals(0.0f, UdpAudioReceiver.calculateRms(new byte[10], 0), 0.0001f);
        assertEquals(0.0f, UdpAudioReceiver.calculateRms(new byte[10], -5), 0.0001f);
        assertEquals(0.0f, UdpAudioReceiver.calculateRms(new byte[1], 1), 0.0001f); // 1 byte is 0 samples
    }

    @Test
    public void testFullStreamingLifecycle() throws Exception {
        // Set up mock desktop UDP server socket
        DatagramSocket serverSocket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        serverSocket.setSoTimeout(3000);
        int serverPort = serverSocket.getLocalPort();

        TestPlaybackEngine testEngine = new TestPlaybackEngine();
        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch audioPacketLatch = new CountDownLatch(1);
        CountDownLatch disconnectedLatch = new CountDownLatch(1);
        AtomicReference<Float> receivedAudioLevel = new AtomicReference<>(0f);

        UdpAudioReceiver receiver = new UdpAudioReceiver(
                "127.0.0.1",
                serverPort,
                testEngine,
                new UdpAudioReceiver.ReceiverListener() {
                    @Override
                    public void onConnected() {
                        connectedLatch.countDown();
                    }

                    @Override
                    public void onAudioPacketReceived(float level) {
                        receivedAudioLevel.set(level);
                        audioPacketLatch.countDown();
                    }

                    @Override
                    public void onTimedOut() {
                    }

                    @Override
                    public void onError(String message) {
                    }

                    @Override
                    public void onDisconnected() {
                        disconnectedLatch.countDown();
                    }
                }
        );

        try {
            receiver.start();
            assertTrue(receiver.isRunning());

            // 1. Verify server receives CONNECT packet from receiver
            byte[] serverBuf = new byte[512];
            DatagramPacket connectPacket = new DatagramPacket(serverBuf, serverBuf.length);
            serverSocket.receive(connectPacket);
            String connectMsg = new String(connectPacket.getData(), 0, connectPacket.getLength(), StandardCharsets.UTF_8);
            assertEquals("CONNECT", connectMsg);

            // Verify receiver triggered onConnected and started engine
            assertTrue(connectedLatch.await(2, TimeUnit.SECONDS));
            assertTrue(testEngine.isPlaying());

            // 2. Server sends audio packet back to receiver
            byte[] audioData = new byte[2048];
            for (int i = 0; i < audioData.length; i += 2) {
                short val = 15000;
                audioData[i] = (byte) (val & 0xFF);
                audioData[i + 1] = (byte) ((val >> 8) & 0xFF);
            }
            DatagramPacket audioPacket = new DatagramPacket(
                    audioData,
                    audioData.length,
                    connectPacket.getAddress(),
                    connectPacket.getPort()
            );
            serverSocket.send(audioPacket);

            // Verify receiver receives audio packet, calculates level, and writes to engine
            assertTrue(audioPacketLatch.await(2, TimeUnit.SECONDS));
            assertTrue(receivedAudioLevel.get() > 0.0f);
            assertEquals(2048, testEngine.getTotalBytesWritten());

            // 3. Stop receiver and verify DISCONNECT signal is received by server
            receiver.stop();
            assertFalse(receiver.isRunning());

            DatagramPacket disconnectPacket = new DatagramPacket(serverBuf, serverBuf.length);
            serverSocket.receive(disconnectPacket);
            String disconnectMsg = new String(disconnectPacket.getData(), 0, disconnectPacket.getLength(), StandardCharsets.UTF_8);
            assertEquals("DISCONNECT", disconnectMsg);
            assertEquals(connectPacket.getPort(), disconnectPacket.getPort());

            assertTrue(disconnectedLatch.await(2, TimeUnit.SECONDS));
            assertFalse(testEngine.isPlaying());

        } finally {
            receiver.stop();
            serverSocket.close();
        }
    }

    @Test
    public void testErrorHandlingInvalidHost() throws Exception {
        CountDownLatch errorLatch = new CountDownLatch(1);
        CountDownLatch disconnectedLatch = new CountDownLatch(1);
        AtomicReference<String> errorReceived = new AtomicReference<>();

        UdpAudioReceiver receiver = new UdpAudioReceiver(
                "invalid.domain.that.does.not.exist.sonicshare",
                50005,
                new TestPlaybackEngine(),
                new UdpAudioReceiver.ReceiverListener() {
                    @Override
                    public void onConnected() {
                    }

                    @Override
                    public void onAudioPacketReceived(float level) {
                    }

                    @Override
                    public void onTimedOut() {
                    }

                    @Override
                    public void onError(String message) {
                        errorReceived.set(message);
                        errorLatch.countDown();
                    }

                    @Override
                    public void onDisconnected() {
                        disconnectedLatch.countDown();
                    }
                }
        );

        receiver.start();

        assertTrue(errorLatch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorReceived.get());
        assertTrue(errorReceived.get().contains("Connection error"));
        assertTrue(disconnectedLatch.await(2, TimeUnit.SECONDS));
        assertFalse(receiver.isRunning());
    }

    @Test
    public void testMultipleStartCallsAreIdempotent() {
        UdpAudioReceiver receiver = new UdpAudioReceiver("127.0.0.1", 50005, new TestPlaybackEngine(), null);
        receiver.start();
        assertTrue(receiver.isRunning());
        receiver.start();
        assertTrue(receiver.isRunning());
        receiver.stop();
        assertFalse(receiver.isRunning());
        receiver.stop();
        assertFalse(receiver.isRunning());
    }

    @Test
    public void testPacketBufferDoesNotTruncateAfterShortPacket() throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        serverSocket.setSoTimeout(3000);
        int serverPort = serverSocket.getLocalPort();

        TestPlaybackEngine testEngine = new TestPlaybackEngine();
        CountDownLatch shortPacketLatch = new CountDownLatch(1);
        CountDownLatch fullPacketLatch = new CountDownLatch(2);

        UdpAudioReceiver receiver = new UdpAudioReceiver(
                "127.0.0.1",
                serverPort,
                testEngine,
                new UdpAudioReceiver.ReceiverListener() {
                    @Override public void onConnected() {}
                    @Override public void onAudioPacketReceived(float level) {
                        shortPacketLatch.countDown();
                        fullPacketLatch.countDown();
                    }
                    @Override public void onTimedOut() {}
                    @Override public void onError(String message) {}
                    @Override public void onDisconnected() {}
                }
        );

        try {
            receiver.start();
            byte[] serverBuf = new byte[512];
            DatagramPacket connectPacket = new DatagramPacket(serverBuf, serverBuf.length);
            serverSocket.receive(connectPacket);

            // 1. Send short packet (e.g. 64 bytes)
            byte[] shortData = new byte[64];
            DatagramPacket shortPacket = new DatagramPacket(
                    shortData,
                    shortData.length,
                    connectPacket.getAddress(),
                    connectPacket.getPort()
            );
            serverSocket.send(shortPacket);
            assertTrue(shortPacketLatch.await(2, TimeUnit.SECONDS));
            assertEquals(64, testEngine.getTotalBytesWritten());

            // 2. Send full packet (2048 bytes)
            byte[] fullData = new byte[2048];
            DatagramPacket fullPacket = new DatagramPacket(
                    fullData,
                    fullData.length,
                    connectPacket.getAddress(),
                    connectPacket.getPort()
            );
            serverSocket.send(fullPacket);
            assertTrue(fullPacketLatch.await(2, TimeUnit.SECONDS));
            // If packet.setLength was missing, total bytes written would be 64 + 64 = 128 instead of 64 + 2048 = 2112
            assertEquals(64 + 2048, testEngine.getTotalBytesWritten());

        } finally {
            receiver.stop();
            serverSocket.close();
        }
    }

    @Test
    public void testRapidRestartLifecycle() throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        serverSocket.setSoTimeout(3000);
        int serverPort = serverSocket.getLocalPort();

        TestPlaybackEngine engine1 = new TestPlaybackEngine();
        CountDownLatch connected1 = new CountDownLatch(1);
        CountDownLatch disconnected1 = new CountDownLatch(1);

        UdpAudioReceiver receiver = new UdpAudioReceiver(
                "127.0.0.1",
                serverPort,
                engine1,
                new UdpAudioReceiver.ReceiverListener() {
                    @Override public void onConnected() { connected1.countDown(); }
                    @Override public void onAudioPacketReceived(float level) {}
                    @Override public void onTimedOut() {}
                    @Override public void onError(String message) {}
                    @Override public void onDisconnected() { disconnected1.countDown(); }
                }
        );

        receiver.start();
        byte[] buf = new byte[512];
        DatagramPacket p1 = new DatagramPacket(buf, buf.length);
        serverSocket.receive(p1);
        assertTrue(connected1.await(2, TimeUnit.SECONDS));

        // Stop session 1
        receiver.stop();
        assertFalse(receiver.isRunning());
        assertTrue(disconnected1.await(2, TimeUnit.SECONDS));

        // Start session 2 immediately on same receiver instance
        TestPlaybackEngine engine2 = new TestPlaybackEngine();
        CountDownLatch connected2 = new CountDownLatch(1);
        CountDownLatch disconnected2 = new CountDownLatch(1);

        UdpAudioReceiver receiver2 = new UdpAudioReceiver(
                "127.0.0.1",
                serverPort,
                engine2,
                new UdpAudioReceiver.ReceiverListener() {
                    @Override public void onConnected() { connected2.countDown(); }
                    @Override public void onAudioPacketReceived(float level) {}
                    @Override public void onTimedOut() {}
                    @Override public void onError(String message) {}
                    @Override public void onDisconnected() { disconnected2.countDown(); }
                }
        );

        receiver2.start();
        DatagramPacket p2 = new DatagramPacket(buf, buf.length);
        serverSocket.receive(p2);
        assertTrue(connected2.await(2, TimeUnit.SECONDS));
        assertTrue(engine2.isPlaying());

        receiver2.stop();
        assertFalse(receiver2.isRunning());
        assertTrue(disconnected2.await(2, TimeUnit.SECONDS));
        assertFalse(engine2.isPlaying());
        serverSocket.close();
    }
}
