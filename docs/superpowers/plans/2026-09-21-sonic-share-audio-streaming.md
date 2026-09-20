# Sonic Share Audio Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a low-latency wireless audio streaming system in Java that captures system audio from a Mac laptop and plays it through an Android phone's speaker over local Wi-Fi.

**Architecture:** The desktop application (Mac) captures 44.1 kHz 16-bit Mono PCM audio via a loopback virtual line (BlackHole) or microphone, fragments it into ~23 ms frames (2048 bytes), and streams raw UDP datagrams to an Android client. The Android client receives the datagrams in a background thread and streams them directly to hardware output using Android's `AudioTrack` in `MODE_STREAM`.

**Tech Stack:** Java 17, Swing (Desktop UI), `javax.sound.sampled`, `java.net.DatagramSocket`, Android SDK (API 26+), Android `AudioTrack`, Gradle.

**Spec:** [`docs/superpowers/specs/2026-09-21-sonic-share-audio-streaming-design.md`](file:///Users/lami/Documents/Java%20Projects/Sonic%20Share/docs/superpowers/specs/2026-09-21-sonic-share-audio-streaming-design.md)

## Global Constraints

- **Language floor**: Java 17 for both Desktop and Android projects.
- **Audio standard**: 44,100 Hz, 16-bit signed PCM, Little-Endian, Mono (1 channel).
- **Packet payload**: 2048 bytes (~23.2 ms of audio per packet).
- **Default Port**: 50005 UDP.
- **Signaling words**: `CONNECT` (client register), `DISCONNECT` (client unregister).
- **Platform compatibility**: Apple Silicon / macOS Sonoma+ and Android 8.0+ (API 26+).

---

### Task 1: Desktop Project Scaffolding & Network Utilities

**Files:**
- Create: `desktop/build.gradle`
- Create: `desktop/settings.gradle`
- Create: `desktop/src/main/java/com/sonicshare/desktop/audio/AudioFormatConfig.java`
- Create: `desktop/src/main/java/com/sonicshare/desktop/net/NetworkUtils.java`
- Test: `desktop/src/test/java/com/sonicshare/desktop/net/NetworkUtilsTest.java`

**Interfaces:**
- Produces:
  - `AudioFormatConfig.SAMPLE_RATE`: float (44100.0f)
  - `AudioFormatConfig.SAMPLE_SIZE_BITS`: int (16)
  - `AudioFormatConfig.CHANNELS`: int (1)
  - `AudioFormatConfig.BUFFER_SIZE`: int (2048)
  - `AudioFormatConfig.DEFAULT_PORT`: int (50005)
  - `AudioFormatConfig.getAudioFormat()`: `javax.sound.sampled.AudioFormat`
  - `NetworkUtils.getLocalIPv4Address()`: `String` (returns active local IP or 127.0.0.1)

- [ ] **Step 1: Create Desktop Gradle project configuration**

Write `desktop/settings.gradle`:
```groovy
rootProject.name = 'sonic-share-desktop'
```

Write `desktop/build.gradle`:
```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'com.sonicshare.desktop.Main'
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write failing test for `NetworkUtils` and `AudioFormatConfig`**

Create `desktop/src/test/java/com/sonicshare/desktop/net/NetworkUtilsTest.java`:
```java
package com.sonicshare.desktop.net;

import com.sonicshare.desktop.audio.AudioFormatConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.sound.sampled.AudioFormat;

public class NetworkUtilsTest {

    @Test
    public void testAudioFormatConfiguration() {
        AudioFormat format = AudioFormatConfig.getAudioFormat();
        assertEquals(44100.0f, format.getSampleRate());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(1, format.getChannels());
        assertTrue(format.isBigEndian() == false);
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
    }

    @Test
    public void testGetLocalIPv4AddressReturnsValidFormat() {
        String ip = NetworkUtils.getLocalIPv4Address();
        assertNotNull(ip);
        assertFalse(ip.isEmpty());
        // Should either be 127.0.0.1 or a valid IPv4 address
        assertTrue(ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$"), "Expected valid IPv4 string, got: " + ip);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run:
```bash
cd desktop && gradle test
```
Expected output: Compilation failure (classes `AudioFormatConfig` and `NetworkUtils` do not exist).

- [ ] **Step 4: Implement `AudioFormatConfig` and `NetworkUtils`**

Create `desktop/src/main/java/com/sonicshare/desktop/audio/AudioFormatConfig.java`:
```java
package com.sonicshare.desktop.audio;

import javax.sound.sampled.AudioFormat;

public class AudioFormatConfig {
    public static final float SAMPLE_RATE = 44100.0f;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1; // Mono
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false; // Little-Endian
    public static final int BUFFER_SIZE = 2048; // ~23.2ms frame size
    public static final int DEFAULT_PORT = 50005;

    public static final String CMD_CONNECT = "CONNECT";
    public static final String CMD_DISCONNECT = "DISCONNECT";

    public static AudioFormat getAudioFormat() {
        return new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );
    }
}
```

Create `desktop/src/main/java/com/sonicshare/desktop/net/NetworkUtils.java`:
```java
package com.sonicshare.desktop.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetworkUtils {

    public static String getLocalIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
```

- [ ] **Step 5: Run tests and verify they pass**

Run:
```bash
cd desktop && gradle test
```
Expected output: `BUILD SUCCESSFUL` (all tests passed).

- [ ] **Step 6: Commit changes**

```bash
git add desktop/
git commit -m "feat: add desktop project scaffold, AudioFormatConfig, and NetworkUtils"
```

---

### Task 2: Desktop UDP Audio Sender & Signaling

**Files:**
- Create: `desktop/src/main/java/com/sonicshare/desktop/net/UdpAudioSender.java`
- Test: `desktop/src/test/java/com/sonicshare/desktop/net/UdpAudioSenderTest.java`

**Interfaces:**
- Consumes: `AudioFormatConfig.DEFAULT_PORT`, `CMD_CONNECT`, `CMD_DISCONNECT`
- Produces:
  - `UdpAudioSender(int port)`
  - `startListening()`: void
  - `stopListening()`: void
  - `sendChunk(byte[] data, int length)`: void
  - `isConnected()`: boolean
  - `getClientAddress()`: InetAddress
  - `setConnectionListener(ConnectionListener listener)`: void

- [ ] **Step 1: Write failing test for `UdpAudioSender`**

Create `desktop/src/test/java/com/sonicshare/desktop/net/UdpAudioSenderTest.java`:
```java
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd desktop && gradle test --tests UdpAudioSenderTest
```
Expected output: Compilation failure (class `UdpAudioSender` does not exist).

- [ ] **Step 3: Implement `UdpAudioSender`**

Create `desktop/src/main/java/com/sonicshare/desktop/net/UdpAudioSender.java`:
```java
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
    private InetAddress clientAddress;
    private int clientPort;
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
                    if (connectionListener != null) {
                        connectionListener.onConnected(clientAddress, clientPort);
                    }
                } else if (AudioFormatConfig.CMD_DISCONNECT.equalsIgnoreCase(message)) {
                    this.isConnected = false;
                    this.clientAddress = null;
                    if (connectionListener != null) {
                        connectionListener.onDisconnected();
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
        if (!isConnected || socket == null || socket.isClosed() || clientAddress == null) {
            return;
        }

        try {
            DatagramPacket packet = new DatagramPacket(data, length, clientAddress, clientPort);
            socket.send(packet);
        } catch (IOException ignored) {
            // Ignore dropped frames in UDP
        }
    }

    public synchronized void stopListening() {
        isRunning = false;
        isConnected = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
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
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
cd desktop && gradle test --tests UdpAudioSenderTest
```
Expected output: `BUILD SUCCESSFUL` (1 test passed).

- [ ] **Step 5: Commit changes**

```bash
git add desktop/
git commit -m "feat: implement UdpAudioSender with CONNECT and DISCONNECT signaling"
```

---

### Task 3: Desktop Audio Device Manager & Capture Engine

**Files:**
- Create: `desktop/src/main/java/com/sonicshare/desktop/audio/AudioDeviceManager.java`
- Create: `desktop/src/main/java/com/sonicshare/desktop/audio/AudioCaptureEngine.java`
- Test: `desktop/src/test/java/com/sonicshare/desktop/audio/AudioCaptureEngineTest.java`

**Interfaces:**
- Consumes: `AudioFormatConfig.getAudioFormat()`, `UdpAudioSender.sendChunk()`
- Produces:
  - `AudioDeviceManager.getAvailableInputMixers()`: `List<Mixer.Info>`
  - `AudioDeviceManager.findBestInputMixer()`: `Mixer.Info` (prioritizes BlackHole, then default input)
  - `AudioCaptureEngine(UdpAudioSender sender)`
  - `AudioCaptureEngine.startCapture(Mixer.Info mixerInfo)`: void
  - `AudioCaptureEngine.stopCapture()`: void
  - `AudioCaptureEngine.setAudioLevelListener(AudioLevelListener listener)`: void

- [ ] **Step 1: Write failing test for `AudioDeviceManager` and `AudioCaptureEngine`**

Create `desktop/src/test/java/com/sonicshare/desktop/audio/AudioCaptureEngineTest.java`:
```java
package com.sonicshare.desktop.audio;

import com.sonicshare.desktop.net.UdpAudioSender;
import org.junit.jupiter.api.Test;
import javax.sound.sampled.Mixer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AudioCaptureEngineTest {

    @Test
    public void testAudioDeviceManagerDiscoversMixers() {
        List<Mixer.Info> mixers = AudioDeviceManager.getAvailableInputMixers();
        assertNotNull(mixers);
        // On any desktop OS with sound support, at least 0 or more mixers will be returned without error
    }

    @Test
    public void testAudioCaptureEngineLifecycle() {
        UdpAudioSender mockSender = new UdpAudioSender(50098);
        AudioCaptureEngine engine = new AudioCaptureEngine(mockSender);
        assertFalse(engine.isCapturing());

        // Stop without start should be a no-op and safe
        assertDoesNotThrow(engine::stopCapture);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd desktop && gradle test --tests AudioCaptureEngineTest
```
Expected output: Compilation failure (classes `AudioDeviceManager` and `AudioCaptureEngine` do not exist).

- [ ] **Step 3: Implement `AudioDeviceManager` and `AudioCaptureEngine`**

Create `desktop/src/main/java/com/sonicshare/desktop/audio/AudioDeviceManager.java`:
```java
package com.sonicshare.desktop.audio;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class AudioDeviceManager {

    public static List<Mixer.Info> getAvailableInputMixers() {
        List<Mixer.Info> inputMixers = new ArrayList<>();
        AudioFormat format = AudioFormatConfig.getAudioFormat();
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(targetInfo)) {
                    inputMixers.add(info);
                }
            } catch (Exception ignored) {
            }
        }
        return inputMixers;
    }

    public static Mixer.Info findBestInputMixer() {
        List<Mixer.Info> mixers = getAvailableInputMixers();
        if (mixers.isEmpty()) {
            return null;
        }

        // Prioritize BlackHole virtual audio loopback
        for (Mixer.Info info : mixers) {
            String name = info.getName().toLowerCase();
            if (name.contains("blackhole")) {
                return info;
            }
        }

        // Fallback to first available supported input mixer (e.g. built-in mic)
        return mixers.get(0);
    }
}
```

Create `desktop/src/main/java/com/sonicshare/desktop/audio/AudioCaptureEngine.java`:
```java
package com.sonicshare.desktop.audio;

import com.sonicshare.desktop.net.UdpAudioSender;

import javax.sound.sampled.*;

public class AudioCaptureEngine {

    public interface AudioLevelListener {
        void onLevelChanged(float rmsLevel);
    }

    private final UdpAudioSender udpAudioSender;
    private TargetDataLine targetLine;
    private volatile boolean isCapturing = false;
    private Thread captureThread;
    private AudioLevelListener levelListener;

    public AudioCaptureEngine(UdpAudioSender udpAudioSender) {
        this.udpAudioSender = udpAudioSender;
    }

    public void setAudioLevelListener(AudioLevelListener listener) {
        this.levelListener = listener;
    }

    public synchronized void startCapture(Mixer.Info mixerInfo) throws LineUnavailableException {
        if (isCapturing) return;

        AudioFormat format = AudioFormatConfig.getAudioFormat();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);

        if (mixerInfo != null) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            targetLine = (TargetDataLine) mixer.getLine(lineInfo);
        } else {
            targetLine = (TargetDataLine) AudioSystem.getLine(lineInfo);
        }

        targetLine.open(format, AudioFormatConfig.BUFFER_SIZE * 4);
        targetLine.start();
        isCapturing = true;

        captureThread = new Thread(this::captureLoop, "AudioCapture-Thread");
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void captureLoop() {
        byte[] buffer = new byte[AudioFormatConfig.BUFFER_SIZE];
        while (isCapturing && targetLine != null && targetLine.isOpen()) {
            int bytesRead = targetLine.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                udpAudioSender.sendChunk(buffer, bytesRead);

                if (levelListener != null) {
                    float rms = calculateRms(buffer, bytesRead);
                    levelListener.onLevelChanged(rms);
                }
            }
        }
    }

    private float calculateRms(byte[] buffer, int length) {
        long sum = 0;
        int samples = length / 2;
        if (samples == 0) return 0f;

        for (int i = 0; i < length - 1; i += 2) {
            // 16-bit little-endian signed
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += (long) sample * sample;
        }

        double mean = (double) sum / samples;
        double rms = Math.sqrt(mean);
        // Normalize 0..32767 to 0.0..1.0
        return (float) Math.min(1.0, rms / 15000.0);
    }

    public synchronized void stopCapture() {
        isCapturing = false;
        if (targetLine != null) {
            try {
                targetLine.stop();
                targetLine.close();
            } catch (Exception ignored) {
            }
            targetLine = null;
        }
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
    }

    public boolean isCapturing() {
        return isCapturing;
    }
}
```

- [ ] **Step 4: Run tests and verify they pass**

Run:
```bash
cd desktop && gradle test --tests AudioCaptureEngineTest
```
Expected output: `BUILD SUCCESSFUL` (all tests passed).

- [ ] **Step 5: Commit changes**

```bash
git add desktop/
git commit -m "feat: add AudioDeviceManager and AudioCaptureEngine"
```

---

### Task 4: Desktop Swing User Interface & Main Entrypoint

**Files:**
- Create: `desktop/src/main/java/com/sonicshare/desktop/ui/AudioLevelMeter.java`
- Create: `desktop/src/main/java/com/sonicshare/desktop/ui/MainWindow.java`
- Create: `desktop/src/main/java/com/sonicshare/desktop/Main.java`

**Interfaces:**
- Consumes: `AudioCaptureEngine`, `UdpAudioSender`, `AudioDeviceManager`, `NetworkUtils`
- Produces:
  - `Main.main(String[] args)`: launches Swing application window.

- [ ] **Step 1: Implement `AudioLevelMeter` custom Swing component**

Create `desktop/src/main/java/com/sonicshare/desktop/ui/AudioLevelMeter.java`:
```java
package com.sonicshare.desktop.ui;

import javax.swing.*;
import java.awt.*;

public class AudioLevelMeter extends JComponent {
    private float level = 0.0f; // 0.0 to 1.0

    public AudioLevelMeter() {
        setPreferredSize(new Dimension(260, 18));
    }

    public void setLevel(float level) {
        this.level = Math.max(0.0f, Math.min(1.0f, level));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Background track
        g2.setColor(new Color(230, 230, 230));
        g2.fillRoundRect(0, 0, width, height, 8, 8);

        // Filled level
        int fillWidth = (int) (width * level);
        if (fillWidth > 0) {
            Color barColor = (level > 0.8f) ? new Color(230, 80, 80) :
                             (level > 0.5f) ? new Color(240, 180, 40) :
                                              new Color(46, 184, 92);
            g2.setColor(barColor);
            g2.fillRoundRect(0, 0, fillWidth, height, 8, 8);
        }

        // Border
        g2.setColor(new Color(180, 180, 180));
        g2.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);
        g2.dispose();
    }
}
```

- [ ] **Step 2: Implement `MainWindow.java`**

Create `desktop/src/main/java/com/sonicshare/desktop/ui/MainWindow.java`:
```java
package com.sonicshare.desktop.ui;

import com.sonicshare.desktop.audio.AudioCaptureEngine;
import com.sonicshare.desktop.audio.AudioDeviceManager;
import com.sonicshare.desktop.audio.AudioFormatConfig;
import com.sonicshare.desktop.net.NetworkUtils;
import com.sonicshare.desktop.net.UdpAudioSender;

import javax.sound.sampled.Mixer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.InetAddress;
import java.util.List;

public class MainWindow extends JFrame {

    private final UdpAudioSender udpAudioSender;
    private final AudioCaptureEngine captureEngine;

    private JLabel ipLabel;
    private JComboBox<MixerItem> deviceCombo;
    private AudioLevelMeter levelMeter;
    private JLabel statusLabel;
    private JButton toggleServerBtn;
    private boolean isServerRunning = false;

    private static class MixerItem {
        final Mixer.Info info;
        MixerItem(Mixer.Info info) { this.info = info; }
        @Override
        public String toString() {
            String name = info.getName();
            return name.length() > 40 ? name.substring(0, 37) + "..." : name;
        }
    }

    public MainWindow() {
        super("Sonic Share — Audio Streamer (Desktop)");
        this.udpAudioSender = new UdpAudioSender(AudioFormatConfig.DEFAULT_PORT);
        this.captureEngine = new AudioCaptureEngine(udpAudioSender);

        initUI();
        setupListeners();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(480, 380);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 24, 20, 24));
        mainPanel.setBackground(Color.WHITE);

        // Header Title
        JLabel titleLabel = new JLabel("Sonic Share");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Stream Mac audio to your Android phone");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(subtitleLabel);
        mainPanel.add(Box.createVerticalStrut(18));

        // IP Card
        JPanel ipCard = new JPanel(new GridLayout(2, 1, 4, 4));
        ipCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));
        ipCard.setBackground(new Color(248, 249, 250));
        ipCard.setMaximumSize(new Dimension(440, 70));

        JLabel ipTitle = new JLabel("YOUR MAC WI-FI IP (ENTER THIS ON PHONE):");
        ipTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        ipTitle.setForeground(new Color(100, 100, 100));

        String localIp = NetworkUtils.getLocalIPv4Address();
        ipLabel = new JLabel(localIp + ":" + AudioFormatConfig.DEFAULT_PORT);
        ipLabel.setFont(new Font("Monospaced", Font.BOLD, 18));
        ipLabel.setForeground(new Color(30, 100, 220));

        ipCard.add(ipTitle);
        ipCard.add(ipLabel);
        mainPanel.add(ipCard);
        mainPanel.add(Box.createVerticalStrut(14));

        // Audio Input Selector
        JPanel devicePanel = new JPanel(new BorderLayout(8, 0));
        devicePanel.setBackground(Color.WHITE);
        devicePanel.setMaximumSize(new Dimension(440, 30));
        JLabel devLabel = new JLabel("Audio Input:");
        devLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        deviceCombo = new JComboBox<>();
        populateAudioDevices();
        devicePanel.add(devLabel, BorderLayout.WEST);
        devicePanel.add(deviceCombo, BorderLayout.CENTER);
        mainPanel.add(devicePanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Live VU Meter
        JPanel meterPanel = new JPanel(new BorderLayout(8, 0));
        meterPanel.setBackground(Color.WHITE);
        meterPanel.setMaximumSize(new Dimension(440, 24));
        JLabel meterLabel = new JLabel("Live Input:");
        meterLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        levelMeter = new AudioLevelMeter();
        meterPanel.add(meterLabel, BorderLayout.WEST);
        meterPanel.add(levelMeter, BorderLayout.CENTER);
        mainPanel.add(meterPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Status Banner
        statusLabel = new JLabel("Status: Stopped", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(new Color(120, 120, 120));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createVerticalStrut(16));

        // Start / Stop Button
        toggleServerBtn = new JButton("Start Audio Server");
        toggleServerBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        toggleServerBtn.setPreferredSize(new Dimension(220, 42));
        toggleServerBtn.setMaximumSize(new Dimension(220, 42));
        toggleServerBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        toggleServerBtn.setBackground(new Color(46, 184, 92));
        toggleServerBtn.setForeground(Color.WHITE);
        toggleServerBtn.setFocusPainted(false);
        toggleServerBtn.addActionListener(e -> toggleServer());
        mainPanel.add(toggleServerBtn);

        setContentPane(mainPanel);
    }

    private void populateAudioDevices() {
        List<Mixer.Info> mixers = AudioDeviceManager.getAvailableInputMixers();
        Mixer.Info best = AudioDeviceManager.findBestInputMixer();
        MixerItem selectedItem = null;

        for (Mixer.Info m : mixers) {
            MixerItem item = new MixerItem(m);
            deviceCombo.addItem(item);
            if (best != null && m.getName().equals(best.getName())) {
                selectedItem = item;
            }
        }
        if (selectedItem != null) {
            deviceCombo.setSelectedItem(selectedItem);
        }
    }

    private void setupListeners() {
        captureEngine.setAudioLevelListener(rms -> SwingUtilities.invokeLater(() -> levelMeter.setLevel(rms)));

        udpAudioSender.setConnectionListener(new UdpAudioSender.ConnectionListener() {
            @Override
            public void onConnected(InetAddress client, int port) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Status: Streaming to " + client.getHostAddress() + ":" + port);
                    statusLabel.setForeground(new Color(46, 184, 92));
                });
            }

            @Override
            public void onDisconnected() {
                SwingUtilities.invokeLater(() -> {
                    if (isServerRunning) {
                        statusLabel.setText("Status: Waiting for Android Phone...");
                        statusLabel.setForeground(new Color(230, 150, 20));
                    }
                });
            }
        });
    }

    private void toggleServer() {
        if (!isServerRunning) {
            try {
                udpAudioSender.startListening();
                MixerItem selected = (MixerItem) deviceCombo.getSelectedItem();
                captureEngine.startCapture(selected != null ? selected.info : null);

                isServerRunning = true;
                toggleServerBtn.setText("Stop Server");
                toggleServerBtn.setBackground(new Color(220, 53, 69));
                statusLabel.setText("Status: Waiting for Android Phone...");
                statusLabel.setForeground(new Color(230, 150, 20));
                deviceCombo.setEnabled(false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to start server: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            captureEngine.stopCapture();
            udpAudioSender.stopListening();
            isServerRunning = false;
            levelMeter.setLevel(0);
            toggleServerBtn.setText("Start Audio Server");
            toggleServerBtn.setBackground(new Color(46, 184, 92));
            statusLabel.setText("Status: Stopped");
            statusLabel.setForeground(new Color(120, 120, 120));
            deviceCombo.setEnabled(true);
        }
    }
}
```

- [ ] **Step 3: Implement `Main.java`**

Create `desktop/src/main/java/com/sonicshare/desktop/Main.java`:
```java
package com.sonicshare.desktop;

import com.sonicshare.desktop.ui.MainWindow;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
```

- [ ] **Step 4: Build desktop application and verify compilation**

Run:
```bash
cd desktop && gradle build
```
Expected output: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit changes**

```bash
git add desktop/
git commit -m "feat: implement MainWindow Swing UI and Main launcher"
```

---

### Task 5: Android Mobile Project Scaffolding & Permissions

**Files:**
- Create: `android/settings.gradle`
- Create: `android/build.gradle`
- Create: `android/app/build.gradle`
- Create: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces:
  - Android Gradle Project configured with Android SDK 34, compile target 34, minSdk 26, Java 17 toolchain.
  - Required permissions declared: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`.

- [ ] **Step 1: Write root Android build and settings files**

Create `android/settings.gradle`:
```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SonicShareAndroid"
include ':app'
```

Create `android/build.gradle`:
```groovy
plugins {
    id 'com.android.application' version '8.2.2' apply false
}
```

- [ ] **Step 2: Write `android/app/build.gradle`**

Create `android/app/build.gradle`:
```groovy
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.sonicshare.android'
    compileSdk 34

    defaultConfig {
        applicationId "com.sonicshare.android"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 3: Write `android/app/src/main/AndroidManifest.xml`**

Create `android/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Sonic Share"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SonicShare">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 4: Commit Android project scaffolding**

```bash
git add android/
git commit -m "feat: add Android mobile project structure and manifest permissions"
```

---

### Task 6: Android Audio Playback Engine & UDP Receiver

**Files:**
- Create: `android/app/src/main/java/com/sonicshare/android/audio/AudioPlaybackEngine.java`
- Create: `android/app/src/main/java/com/sonicshare/android/net/UdpAudioReceiver.java`
- Test: `android/app/src/test/java/com/sonicshare/android/net/AudioProtocolConstantsTest.java`

**Interfaces:**
- Produces:
  - `AudioPlaybackEngine.start()`: void
  - `AudioPlaybackEngine.writeAudio(byte[] data, int offset, int length)`: int
  - `AudioPlaybackEngine.stop()`: void
  - `UdpAudioReceiver(String serverIp, int serverPort, AudioPlaybackEngine engine, ReceiverListener listener)`
  - `UdpAudioReceiver.start()`: void
  - `UdpAudioReceiver.stop()`: void

- [ ] **Step 1: Write Unit Test for Audio Protocol Constants**

Create `android/app/src/test/java/com/sonicshare/android/net/AudioProtocolConstantsTest.java`:
```java
package com.sonicshare.android.net;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioProtocolConstantsTest {

    @Test
    public void testProtocolDefaults() {
        assertEquals("CONNECT", UdpAudioReceiver.CMD_CONNECT);
        assertEquals("DISCONNECT", UdpAudioReceiver.CMD_DISCONNECT);
        assertEquals(2048, UdpAudioReceiver.BUFFER_SIZE);
        assertEquals(44100, AudioPlaybackEngine.SAMPLE_RATE);
    }
}
```

- [ ] **Step 2: Implement `AudioPlaybackEngine.java`**

Create `android/app/src/main/java/com/sonicshare/android/audio/AudioPlaybackEngine.java`:
```java
package com.sonicshare.android.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AudioPlaybackEngine {

    public static final int SAMPLE_RATE = 44100;
    private AudioTrack audioTrack;
    private volatile boolean isPlaying = false;

    public synchronized void start() {
        if (isPlaying) return;

        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelConfig, audioEncoding);
        int bufferSize = Math.max(minBufferSize * 2, 4096);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(audioEncoding)
                .setChannelMask(channelConfig)
                .build();

        audioTrack = new AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        audioTrack.play();
        isPlaying = true;
    }

    public int writeAudio(byte[] data, int offset, int length) {
        if (!isPlaying || audioTrack == null) return 0;
        return audioTrack.write(data, offset, length);
    }

    public synchronized void stop() {
        isPlaying = false;
        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
```

- [ ] **Step 3: Implement `UdpAudioReceiver.java`**

Create `android/app/src/main/java/com/sonicshare/android/net/UdpAudioReceiver.java`:
```java
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
        workerThread.start();
    }

    private void receiveLoop() {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(3000); // 3-second timeout to detect dropped Wi-Fi
            InetAddress serverAddr = InetAddress.getByName(serverIp);

            // Send CONNECT handshake
            byte[] connectBytes = CMD_CONNECT.getBytes(StandardCharsets.UTF_8);
            DatagramPacket connectPacket = new DatagramPacket(connectBytes, connectBytes.length, serverAddr, serverPort);
            socket.send(connectPacket);

            playbackEngine.start();
            if (listener != null) listener.onConnected();

            byte[] receiveBuffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            while (isRunning) {
                try {
                    socket.receive(packet);
                    int length = packet.getLength();
                    if (length > 0) {
                        playbackEngine.writeAudio(packet.getData(), 0, length);
                        if (listener != null) {
                            float level = calculateRms(packet.getData(), length);
                            listener.onAudioPacketReceived(level);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    if (isRunning && listener != null) {
                        listener.onTimedOut();
                    }
                }
            }

            // Send DISCONNECT signal before exiting
            try {
                byte[] disconnectBytes = CMD_DISCONNECT.getBytes(StandardCharsets.UTF_8);
                DatagramPacket disconnectPacket = new DatagramPacket(disconnectBytes, disconnectBytes.length, serverAddr, serverPort);
                socket.send(disconnectPacket);
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            if (listener != null && isRunning) {
                listener.onError("Connection error: " + e.getMessage());
            }
        } finally {
            cleanup();
            if (listener != null) listener.onDisconnected();
        }
    }

    private float calculateRms(byte[] buffer, int length) {
        long sum = 0;
        int samples = length / 2;
        if (samples == 0) return 0f;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sum / samples);
        return (float) Math.min(1.0, rms / 15000.0);
    }

    private void cleanup() {
        playbackEngine.stop();
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
    }

    public synchronized void stop() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
```

- [ ] **Step 4: Commit Audio Playback Engine and UDP Receiver**

```bash
git add android/
git commit -m "feat: implement Android AudioPlaybackEngine and UdpAudioReceiver"
```

---

### Task 7: Android UI Layout, Styles, & Activity Lifecycle

**Files:**
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/colors.xml`
- Create: `android/app/src/main/res/values/themes.xml`
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Create: `android/app/src/main/java/com/sonicshare/android/MainActivity.java`

**Interfaces:**
- Produces:
  - Working Android `MainActivity` with Material Design card, IP and port inputs, connect/disconnect button, audio VU progress bar, wake lock management, and persistent IP memory.

- [ ] **Step 1: Write resource XML files (`strings.xml`, `colors.xml`, `themes.xml`)**

Create `android/app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Sonic Share</string>
    <string name="title_app">Sonic Share</string>
    <string name="subtitle_app">Turn your phone into a laptop speaker</string>
    <string name="server_ip_label">Laptop Wi-Fi IP Address</string>
    <string name="server_port_label">Port</string>
    <string name="btn_connect">Connect &amp; Listen</string>
    <string name="btn_stop">Stop Listening</string>
    <string name="status_disconnected">Status: Disconnected</string>
    <string name="status_streaming">Status: Streaming Live Audio</string>
    <string name="status_timeout">Status: Waiting for audio packet...</string>
</resources>
```

Create `android/app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="primary">#1E88E5</color>
    <color name="primary_dark">#1565C0</color>
    <color name="accent">#2EB85C</color>
    <color name="danger">#E53935</color>
    <color name="background">#F5F6F8</color>
    <color name="surface">#FFFFFF</color>
    <color name="text_primary">#212121</color>
    <color name="text_secondary">#757575</color>
</resources>
```

Create `android/app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.SonicShare" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryVariant">@color/primary_dark</item>
        <item name="colorSecondary">@color/accent</item>
        <item name="android:statusBarColor">@color/primary_dark</item>
    </style>
</resources>
```

- [ ] **Step 2: Write layout XML `activity_main.xml`**

Create `android/app/src/main/res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background"
    android:padding="24dp"
    android:gravity="center_horizontal">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/title_app"
        android:textSize="26sp"
        android:textStyle="bold"
        android:textColor="@color/text_primary"
        android:layout_marginTop="20dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/subtitle_app"
        android:textSize="14sp"
        android:textColor="@color/text_secondary"
        android:layout_marginBottom="28dp" />

    <!-- Configuration Card -->
    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:cardCornerRadius="14dp"
        app:cardElevation="3dp"
        app:cardBackgroundColor="@color/surface"
        app:contentPadding="18dp"
        android:layout_marginBottom="24dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/server_ip_label"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
                android:layout_marginBottom="12dp">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/ipEditText"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textUri"
                    android:fontFamily="monospace"
                    android:text="192.168.1." />
            </com.google.android.material.textfield.TextInputLayout>

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/server_port_label"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/portEditText"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="number"
                    android:fontFamily="monospace"
                    android:text="50005" />
            </com.google.android.material.textfield.TextInputLayout>

        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>

    <!-- Audio VU Meter Indicator -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Audio Output Level"
        android:textSize="12sp"
        android:textColor="@color/text_secondary"
        android:layout_marginBottom="6dp" />

    <ProgressBar
        android:id="@+id/audioLevelBar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="14dp"
        android:max="100"
        android:progress="0"
        android:progressDrawable="@android:drawable/progress_horizontal"
        android:layout_marginBottom="24dp" />

    <!-- Status Banner -->
    <TextView
        android:id="@+id/statusTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/status_disconnected"
        android:textSize="15sp"
        android:textStyle="bold"
        android:textColor="@color/text_secondary"
        android:layout_marginBottom="24dp" />

    <!-- Connect / Stop Button -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/toggleButton"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="@string/btn_connect"
        android:textSize="16sp"
        android:textStyle="bold"
        app:cornerRadius="12dp"
        android:backgroundTint="@color/accent" />

</LinearLayout>
```

- [ ] **Step 3: Implement `MainActivity.java`**

Create `android/app/src/main/java/com/sonicshare/android/MainActivity.java`:
```java
package com.sonicshare.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.sonicshare.android.audio.AudioPlaybackEngine;
import com.sonicshare.android.net.UdpAudioReceiver;

public class MainActivity extends AppCompatActivity implements UdpAudioReceiver.ReceiverListener {

    private static final String PREFS_NAME = "SonicSharePrefs";
    private static final String KEY_LAST_IP = "last_ip";
    private static final String KEY_LAST_PORT = "last_port";

    private EditText ipEditText;
    private EditText portEditText;
    private ProgressBar audioLevelBar;
    private TextView statusTextView;
    private Button toggleButton;

    private AudioPlaybackEngine playbackEngine;
    private UdpAudioReceiver audioReceiver;
    private PowerManager.WakeLock wakeLock;
    private boolean isConnectingOrStreaming = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipEditText = findViewById(R.id.ipEditText);
        portEditText = findViewById(R.id.portEditText);
        audioLevelBar = findViewById(R.id.audioLevelBar);
        statusTextView = findViewById(R.id.statusTextView);
        toggleButton = findViewById(R.id.toggleButton);

        playbackEngine = new AudioPlaybackEngine();

        // Restore saved server settings
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedIp = prefs.getString(KEY_LAST_IP, "192.168.1.");
        String savedPort = prefs.getString(KEY_LAST_PORT, "50005");
        ipEditText.setText(savedIp);
        portEditText.setText(savedPort);

        toggleButton.setOnClickListener(v -> toggleConnection());

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SonicShare::StreamLock");
        }
    }

    private void toggleConnection() {
        if (!isConnectingOrStreaming) {
            String ip = ipEditText.getText().toString().trim();
            String portStr = portEditText.getText().toString().trim();

            if (ip.isEmpty() || !ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
                Toast.makeText(this, "Please enter a valid IPv4 address", Toast.LENGTH_SHORT).show();
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid port number", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save IP for next time
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_IP, ip)
                    .putString(KEY_LAST_PORT, portStr)
                    .apply();

            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(120 * 60 * 1000L /* 2 hours max */);
            }

            audioReceiver = new UdpAudioReceiver(ip, port, playbackEngine, this);
            audioReceiver.start();

            isConnectingOrStreaming = true;
            ipEditText.setEnabled(false);
            portEditText.setEnabled(false);
            toggleButton.setText(R.string.btn_stop);
            toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.danger));
            statusTextView.setText(R.string.status_streaming);
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.accent));

        } else {
            stopStreaming();
        }
    }

    private void stopStreaming() {
        if (audioReceiver != null) {
            audioReceiver.stop();
            audioReceiver = null;
        }
        playbackEngine.stop();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        isConnectingOrStreaming = false;
        ipEditText.setEnabled(true);
        portEditText.setEnabled(true);
        audioLevelBar.setProgress(0);
        toggleButton.setText(R.string.btn_connect);
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.accent));
        statusTextView.setText(R.string.status_disconnected);
        statusTextView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            statusTextView.setText(R.string.status_streaming);
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.accent));
        });
    }

    @Override
    public void onAudioPacketReceived(float level) {
        runOnUiThread(() -> {
            int progress = (int) (level * 100);
            audioLevelBar.setProgress(progress);
        });
    }

    @Override
    public void onTimedOut() {
        runOnUiThread(() -> {
            statusTextView.setText(R.string.status_timeout);
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            audioLevelBar.setProgress(0);
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            stopStreaming();
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(this::stopStreaming);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }
}
```

- [ ] **Step 4: Commit Android UI and Activity**

```bash
git add android/
git commit -m "feat: implement Android MainActivity UI, SharedPreferences, and WakeLock"
```

---

### Task 8: End-to-End Testing, BlackHole Audio Routing & Documentation

**Files:**
- Create: `README.md`
- Test: Complete loopback test script or end-to-end verification

**Interfaces:**
- Produces:
  - Ready-to-demo system with clear step-by-step instructions for installing BlackHole 2ch on macOS and running both desktop and Android clients.

- [ ] **Step 1: Write comprehensive `README.md` with demo and setup instructions**

Create `README.md`:
```markdown
# Sonic Share 🎵

Wireless audio streaming from your Mac laptop to an Android phone over local Wi-Fi. Turn any Android phone into a real-time, low-latency speaker for laptops with broken speakers.

## Features
- **Ultra-low latency (~25-40ms)**: Raw PCM audio streamed over UDP datagrams.
- **Pure Java**: Built with Java 17, `javax.sound.sampled`, Swing, and Android SDK without native C++ compilation headaches.
- **Live VU Meters**: Real-time visual audio level feedback on both Mac and Android phone.
- **Auto-saved IP**: Remembers your Mac IP address on Android.

---

## 1. Mac Audio Routing Setup (BlackHole)
Because macOS restricts applications from recording speaker audio directly:

1. Install **BlackHole 2ch** (free, open source):
   ```bash
   brew install blackhole-2ch
   ```
2. In macOS **System Settings ➔ Sound ➔ Output**, select **BlackHole 2ch**.
   *(Audio from YouTube, Spotify, VLC will now route directly to BlackHole).*
3. *(Optional Fallback)*: If BlackHole is not installed, Sonic Share will automatically use the **Built-in Microphone** for testing and presentation demos.

---

## 2. Running the Desktop Application (Mac)
```bash
cd desktop
gradle run
```
1. The window will open displaying your Mac's Wi-Fi IP (e.g. `192.168.1.45:50005`).
2. Select your audio input (defaults to BlackHole).
3. Click **"Start Audio Server"**.

---

## 3. Running the Mobile Application (Android)
1. Open the `android/` directory in **Android Studio**.
2. Connect your Android phone via USB (or use an emulator on the same Wi-Fi).
3. Click **Run ('app')** in Android Studio to install the app.
4. On your phone:
   - Enter the Mac IP address displayed on your Mac's screen.
   - Tap **"Connect & Listen"**.
5. Play any video or song on your Mac — sound will play out of your phone's speaker!
```

- [ ] **Step 2: Verify Desktop build and test run**

Run:
```bash
cd desktop && gradle build
```
Expected output: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit README and final project integration**

```bash
git add README.md
git commit -m "docs: add project README with BlackHole setup and demo guide"
```
