# Sonic Share — Architecture & Design Specification

**Date**: 2026-09-21  
**Project**: Sonic Share (Wireless Laptop-to-Phone Audio Streaming)  
**Target Submission Deadline**: Tomorrow  
**Platform**: macOS (Desktop Sender in Java) + Android (Mobile Receiver in Java)  

---

## 1. Overview & Problem Statement

Many laptops suffer from broken internal hardware speakers or poor audio output. Sonic Share is an ultra-low-latency wireless audio streaming system designed to turn an Android phone into a high-quality external speaker for a Mac laptop over local Wi-Fi.

The system is built entirely in Java to fulfill semester course requirements, utilizing native Java standard libraries (`javax.sound.sampled`, `java.net.DatagramSocket`) on the desktop and native Android APIs (`AudioTrack`, `DatagramSocket`) on mobile.

---

## 2. System Architecture

```
+-------------------------------------------------------------------------+
|                          Mac Desktop (Sender)                           |
|                                                                         |
|  [macOS System Audio (YouTube, Spotify, Media Players)]                 |
|                                 |                                       |
|                                 v                                       |
|                      [BlackHole 2ch Virtual Driver]                     |
|                                 |                                       |
|                                 v                                       |
|                    AudioCaptureEngine (TargetDataLine)                  |
|               (Format: 44.1kHz, 16-bit Mono PCM, 2048-byte chunks)      |
|                                 |                                       |
|                                 v                                       |
|              UdpAudioSender (DatagramSocket on port 50005)              |
+---------------------------------|---------------------------------------+
                                  |
                        Local Wi-Fi Network
                        UDP Datagram Packets (~23ms latency/frame)
                                  |
+---------------------------------|---------------------------------------+
|                       Android Phone (Receiver)                          |
|                                 |                                       |
|                                 v                                       |
|              UdpAudioReceiver (DatagramSocket)                          |
|                                 |                                       |
|                                 v (Raw PCM bytes)                       |
|           AudioPlaybackEngine (AudioTrack in MODE_STREAM)               |
|                                 |                                       |
|                                 v                                       |
|                   [Android Hardware Speaker]                            |
+-------------------------------------------------------------------------+
```

---

## 3. Audio & Network Specifications

### 3.1 Audio Parameters
* **Sampling Rate**: `44,100 Hz` (CD quality, universal hardware compatibility).
* **Bit Depth**: `16-bit Signed PCM` (Little-Endian).
* **Channels**: `1 (Mono)` — cuts bandwidth by 50% (~705 kbps vs 1.4 Mbps), eliminating Wi-Fi packet drops while delivering crystal-clear sound on phone speakers.
* **Packet / Frame Size**: `2048 bytes` (1024 samples = **~23.2 ms** of sound per packet), ensuring instant real-time synchronization.

### 3.2 Network Protocol (UDP Direct Pairing)
* **Transport**: UDP (`DatagramSocket`) for near-zero transmission latency.
* **Desktop Default Port**: `50005` (configurable).
* **Signaling Messages**:
  * `CONNECT`: Sent from Android to Mac desktop upon pressing "Connect".
  * `DISCONNECT`: Sent when either client or desktop stops streaming.
  * Audio Payloads: Continuous binary datagrams containing 2048 bytes of raw PCM audio.

---

## 4. Mac Desktop Application (Sender)

### 4.1 Modules & Responsibilities
1. **`Main.java`**: Application entry point, initializes Swing GUI and dependency injection.
2. **`AudioCaptureEngine.java`**:
   * Inspects available mixers via `AudioSystem.getMixerInfo()`.
   * Opens a `TargetDataLine` with the requested `AudioFormat(44100.0f, 16, 1, true, false)`.
   * Executes a dedicated capture loop on a high-priority background thread:
     ```java
     byte[] buffer = new byte[2048];
     while (isStreaming) {
         int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
         if (bytesRead > 0) {
             udpAudioSender.sendChunk(buffer, bytesRead);
             audioLevelMeter.updateLevel(buffer, bytesRead);
         }
     }
     ```
3. **`AudioDeviceManager.java`**:
   * Enumerates system audio input devices.
   * Auto-detects **BlackHole 2ch** (virtual loopback driver); falls back to default microphone if BlackHole is not detected.
4. **`UdpAudioSender.java`**:
   * Binds a `DatagramSocket` on port `50005`.
   * Listens on a control thread for incoming `CONNECT` packets from the Android client.
   * Stores client `InetAddress` and port; streams audio chunks directly to that endpoint.
5. **`NetworkUtils.java`**:
   * Discovers active local Wi-Fi IPv4 address using `NetworkInterface.getNetworkInterfaces()` (filtering out loopback and virtual interfaces).
6. **`MainWindow.java` (Swing GUI)**:
   * Displays local IP and port prominently (e.g., `192.168.1.45:50005`).
   * Dropdown to select Audio Input Device.
   * Live audio VU meter bar (green/yellow) confirming audio is actively being captured.
   * Connection status banner (Waiting for phone / Connected to Android / Stopped).
   * Start Server and Stop Server controls.

---

## 5. Android Mobile Application (Receiver)

### 5.1 Modules & Responsibilities
1. **`MainActivity.java`**:
   * Input fields for Server IP and Port (pre-filled with `50005`).
   * Saves entered IP address in `SharedPreferences` for auto-fill on next launch.
   * Connect / Disconnect button.
   * Status indicator and live audio output visualizer.
2. **`AudioPlaybackEngine.java`**:
   * Manages Android `AudioTrack` configured with:
     * `AudioAttributes`: `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`
     * `AudioFormat`: `ENCODING_PCM_16BIT`, `44100 Hz`, `CHANNEL_OUT_MONO`
     * Buffer size: `AudioTrack.getMinBufferSize(...) * 2` (double-buffering prevents underruns).
     * Mode: `AudioTrack.MODE_STREAM`.
3. **`UdpAudioReceiver.java`**:
   * Background worker thread with a `DatagramSocket`.
   * Sends `CONNECT` packet to the Mac IP.
   * Enters receive loop: receives 2048-byte packets and writes directly to `AudioTrack.write()`.
   * Graceful termination: sends `DISCONNECT`, stops and releases `AudioTrack`, and closes socket.
4. **Permissions**:
   * `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`.

---

## 6. System Setup & Prerequisites (Mac Audio Routing)

Because macOS restricts recording internal audio output directly:
1. **Install Virtual Driver**:
   ```bash
   brew install blackhole-2ch
   ```
2. **Configure macOS Sound Output**:
   * In macOS **System Settings ➔ Sound ➔ Output**, select **BlackHole 2ch**.
   * Any sound played on the Mac is routed directly to BlackHole.
3. **Java Capture**:
   * The Java desktop app selects **BlackHole 2ch** as the input line, reading the audio cleanly.
4. **Fallback Option**:
   * If BlackHole is not installed, the app can read from the Mac's **Built-in Microphone**, allowing live testing and demonstration anywhere.

---

## 7. Error Handling & Edge Cases

| Scenario | Handling Strategy |
|----------|-------------------|
| Wi-Fi drops / packet loss | Android socket timeout (`setSoTimeout(3000)`) pauses playback and displays "Waiting for signal..." without crashing. UDP naturally drops missed frames without freezing. |
| Port 50005 in use on Mac | Catch `BindException`, notify user or attempt sequential fallback port (`50006`). |
| App paused or phone goes to sleep | Acquire `WifiLock` and `WakeLock` on Android to maintain continuous streaming. |
| Buffer underruns | Android buffer sized to `minBufferSize * 2` to cushion against minor Wi-Fi jitter. |
| Clean shutdown | Explicit `DISCONNECT` packets and `volatile boolean` flags ensure sockets and audio lines close cleanly. |

---

## 8. Verification & Testing Plan

1. **Mac Desktop Audio Level Test**:
   * Launch Desktop app, select Microphone or BlackHole.
   * Play sound / speak into mic. Confirm the Swing level meter moves in real time.
2. **Android Playback Test**:
   * Verify `AudioTrack` initializes and plays a test tone or sine wave.
3. **Network Connection Test**:
   * Launch Desktop app.
   * Launch Android app on phone (connected to same Wi-Fi or phone hotspot).
   * Enter Mac IP and tap "Connect". Confirm Desktop UI registers connection.
4. **End-to-End Real World Test**:
   * Route Mac output to BlackHole.
   * Play a YouTube video or music file on Mac.
   * Verify clear, low-latency audio output from the phone's speaker.
