package com.sonicshare.desktop.net;

import com.sonicshare.desktop.audio.AudioFormatConfig;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class UdpAudioSender {

    public interface ConnectionListener {
        void onConnected(InetAddress client, int port);
        void onDisconnected();
    }

    private final int port;
    private DatagramSocket socket;
    private volatile boolean isRunning = false;
    private volatile boolean isConnected = false;
    private volatile InetAddress clientAddress;
    private volatile int clientPort;
    private ConnectionListener connectionListener;
    private Thread listenerThread;

    public UdpAudioSender(int port) {
        this.port = port;
    }

    public synchronized void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public synchronized void startListening() throws SocketException {
        if (isRunning) return;
        socket = new DatagramSocket(port);
        isRunning = true;

        listenerThread = new Thread(this::listenLoop, "UdpAudioSender-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        byte[] buffer = new byte[512];
        while (isRunning && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                if (AudioFormatConfig.CMD_CONNECT.equalsIgnoreCase(message)) {
                    this.clientAddress = packet.getAddress();
                    this.clientPort = packet.getPort();
                    this.isConnected = true;
                    ConnectionListener listener = this.connectionListener;
                    if (listener != null) {
                        listener.onConnected(clientAddress, clientPort);
                    }
                } else if (AudioFormatConfig.CMD_DISCONNECT.equalsIgnoreCase(message)) {
                    this.isConnected = false;
                    this.clientAddress = null;
                    ConnectionListener listener = this.connectionListener;
                    if (listener != null) {
                        listener.onDisconnected();
                    }
                }
            } catch (SocketException e) {
                // Socket closed on stop
                break;
            } catch (IOException e) {
                // Log and continue
            }
        }
    }

    public void sendChunk(byte[] data, int length) {
        InetAddress target = this.clientAddress;
        int targetPort = this.clientPort;
        DatagramSocket currentSocket = this.socket;

        if (!isConnected || currentSocket == null || currentSocket.isClosed() || target == null) {
            return;
        }

        try {
            DatagramPacket packet = new DatagramPacket(data, length, target, targetPort);
            currentSocket.send(packet);
        } catch (IOException ignored) {
            // Ignore dropped frames in UDP
        }
    }

    public synchronized void stopListening() {
        isRunning = false;
        isConnected = false;
        clientAddress = null;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
            try {
                listenerThread.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public InetAddress getClientAddress() {
        return clientAddress;
    }

    public int getClientPort() {
        return clientPort;
    }
}
