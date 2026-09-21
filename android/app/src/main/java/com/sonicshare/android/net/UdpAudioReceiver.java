package com.sonicshare.android.net;

import com.sonicshare.android.audio.AudioPlaybackEngine;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class UdpAudioReceiver {

    public static final String CMD_CONNECT = "CONNECT";
    public static final String CMD_DISCONNECT = "DISCONNECT";
    public static final int BUFFER_SIZE = 2048;

    public interface ReceiverListener {
        void onConnected();
        void onAudioPacketReceived(float level);
        void onTimedOut();
        void onError(String message);
        void onDisconnected();
    }

    private final String serverIp;
    private final int serverPort;
    private final AudioPlaybackEngine playbackEngine;
    private final ReceiverListener listener;

    private DatagramSocket socket;
    private volatile boolean isRunning = false;
    private volatile InetAddress serverAddr;
    private Thread workerThread;

    public UdpAudioReceiver(String serverIp, int serverPort, AudioPlaybackEngine playbackEngine, ReceiverListener listener) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.playbackEngine = playbackEngine;
        this.listener = listener;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;

        workerThread = new Thread(this::receiveLoop, "UdpAudioReceiver-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void receiveLoop() {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(3000); // 3-second timeout to detect dropped Wi-Fi
            serverAddr = InetAddress.getByName(serverIp);

            // Send CONNECT handshake
            byte[] connectBytes = CMD_CONNECT.getBytes(StandardCharsets.UTF_8);
            DatagramPacket connectPacket = new DatagramPacket(connectBytes, connectBytes.length, serverAddr, serverPort);
            socket.send(connectPacket);

            if (playbackEngine != null) {
                playbackEngine.start();
            }
            if (listener != null) {
                try {
                    listener.onConnected();
                } catch (Exception ignored) {
                }
            }

            byte[] receiveBuffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            while (isRunning) {
                try {
                    packet.setLength(receiveBuffer.length);
                    socket.receive(packet);
                    int length = packet.getLength();
                    if (length > 0) {
                        if (playbackEngine != null) {
                            playbackEngine.writeAudio(packet.getData(), 0, length);
                        }
                        if (listener != null) {
                            float level = calculateRms(packet.getData(), length);
                            try {
                                listener.onAudioPacketReceived(level);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (SocketTimeoutException e) {
                    if (isRunning && listener != null) {
                        try {
                            listener.onTimedOut();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

        } catch (Exception e) {
            if (listener != null && isRunning) {
                try {
                    listener.onError("Connection error: " + e.getMessage());
                } catch (Exception ignored) {
                }
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                sendDisconnect(socket, serverAddr, serverPort);
            }
            cleanup();
            if (listener != null) {
                try {
                    listener.onDisconnected();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void sendDisconnect(DatagramSocket sock, InetAddress addr, int port) {
        if (sock == null || sock.isClosed()) return;
        try {
            if (addr == null) {
                addr = InetAddress.getByName(serverIp);
            }
            byte[] disconnectBytes = CMD_DISCONNECT.getBytes(StandardCharsets.UTF_8);
            DatagramPacket disconnectPacket = new DatagramPacket(disconnectBytes, disconnectBytes.length, addr, port);
            sock.send(disconnectPacket);
        } catch (Exception ignored) {
        }
    }

    static float calculateRms(byte[] buffer, int length) {
        if (buffer == null || length <= 0) return 0f;
        int clampedLength = Math.min(length, buffer.length);
        int samples = clampedLength / 2;
        if (samples == 0) return 0f;
        long sum = 0;
        for (int i = 0; i < clampedLength - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sum / samples);
        return (float) Math.min(1.0, rms / 15000.0);
    }

    private void cleanup() {
        isRunning = false;
        if (playbackEngine != null) {
            playbackEngine.stop();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
    }

    public synchronized void stop() {
        if (!isRunning && workerThread == null) return;
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            sendDisconnect(socket, serverAddr, serverPort);
            socket.close();
        }
        if (workerThread != null) {
            Thread current = Thread.currentThread();
            if (workerThread != current) {
                workerThread.interrupt();
                try {
                    workerThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            workerThread = null;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
