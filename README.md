# Sonic Share 🎵

Wireless audio streaming from your Mac laptop to an Android phone over local Wi-Fi. Turn any Android phone into a real-time, low-latency speaker for laptops with broken speakers.

## Features
- **Ultra-low latency (~25-40ms)**: Raw PCM audio streamed over UDP datagrams without container or transcoding overhead.
- **Pure Java**: Built with Java 17, `javax.sound.sampled`, Swing, and Android SDK without native C++ compilation headaches.
- **Live VU Meters**: Real-time visual audio level feedback on both Mac and Android phone.
- **Auto-saved IP**: Remembers your Mac IP address and port on Android via SharedPreferences.
- **Resilient Streaming**: Auto-detection of network interfaces, client timeout detection, and partial wake locks for continuous background playback.

---

## 1. Mac Audio Capture (Native ScreenCaptureKit)
Sonic Share captures system/device audio directly using macOS **ScreenCaptureKit**:

- **Zero Drivers Required**: No need to install BlackHole, Soundflower, or create virtual MIDI aggregate devices.
- **Simultaneous Playback**: Your Mac laptop speakers and your Android phone can play the exact same audio at the same time.
- **Mute Independence**: ScreenCaptureKit captures system audio pre-fader. **Even if you mute your laptop or turn its volume down to 0, your Android phone continues receiving and playing the sound seamlessly!**
- **Zero Microphone Access**: Microphone input is **completely removed**. The app will never listen to or stream from your microphone under any circumstance.
- **Permission**: The first time you start the server on macOS, grant **"Screen & System Audio Recording"** permission in:
  **System Settings ➔ Privacy & Security ➔ Screen & System Audio Recording**.

---

## 2. Running on Linux Mint

### Prerequisites
Install Java 17 on Linux Mint:
```bash
sudo apt update && sudo apt install -y openjdk-17-jre
```

### Run
From the `desktop/` directory, simply run:
```bash
cd desktop
./run-linux.sh
```
Or execute the standalone JAR directly:
```bash
java -jar build/libs/sonic-share-desktop.jar
```

### Audio Routing on Linux Mint
Linux Mint uses PulseAudio / PipeWire out of the box:
1. Sonic Share automatically detects and selects your system's **"Monitor of Built-in Audio Analog Stereo"** (the loopback stream of your computer speakers/headphones).
2. To fine-tune recording inputs on Linux Mint, you can install PulseAudio Volume Control:
   ```bash
   sudo apt install -y pavucontrol
   pavucontrol
   ```
   Under the **Recording** tab, you will see Sonic Share capturing from *Monitor of Built-in Audio*.

---

## 3. Running the Desktop Application (macOS)

### Prerequisites
- JDK 17 or higher
- Gradle (or use the system `gradle` command)

### Run
```bash
cd desktop
gradle run
```
1. The window will open displaying your Mac's Wi-Fi IP and port (e.g. `192.168.1.45:50005`).
2. Audio Source defaults to **macOS System Audio (ScreenCaptureKit)**.
3. Click **"Start Audio Server"**.
4. The status will display `Listening for connection...`.

---

## 4. Running the Mobile Application (Android)

### Prerequisites
- Android Studio Ladybug / Iguana or newer
- Android SDK 26+ (Android 8.0 Oreo or newer; Target SDK 34)
- Physical Android phone or emulator connected to the **same Wi-Fi network** as your Mac

### Installation
1. Open the `android/` directory in **Android Studio**.
2. Connect your Android phone via USB (with USB Debugging enabled) or start an emulator.
3. Click **Run ('app')** in Android Studio (or run `./gradlew installDebug` from the `android/` directory).

### Connecting
1. On your phone:
   - Enter the Mac IP address displayed on your Mac's screen (e.g., `192.168.1.45`).
   - Default Port is `50005`.
   - Tap **"Connect & Listen"**.
2. Play any video or song on your Mac — sound will play out of your phone's speaker with real-time VU meter feedback!
3. Tap **"Stop Listening"** when finished.

---

## 5. Audio Streaming Protocol & Architecture

```
+-------------------------------------------------------------+
|                     Desktop App (Linux / Mac)               |
|                                                             |
|  [System Audio] -> [Monitor / BlackHole / Built-in Mic]     |
|                          |                                  |
|                 AudioCaptureEngine                          |
|             (TargetDataLine, 44.1kHz Mono PCM)              |
|                          | 2048-byte chunks                 |
|                          v                                  |
|                   UdpAudioSender                            |
|             (DatagramSocket Port: 50005)                    |
+--------------------------|----------------------------------+
                           |
                           | UDP Datagrams
                           | (Local Wi-Fi)
                           v
+-------------------------------------------------------------+
|                     Android Mobile App                      |
|                                                             |
|                   UdpAudioReceiver                          |
|             (DatagramSocket, Handshake: CONNECT)            |
|                          | 2048-byte PCM chunks             |
|                          v                                  |
|                 AudioPlaybackEngine                         |
|           (AudioTrack, STREAM_MUSIC, Low Latency)           |
|                          |                                  |
|                          v                                  |
|                    Phone Speaker                            |
+-------------------------------------------------------------+
```

- **Format**: 44,100 Hz, 16-bit, Mono, Signed, Little-Endian PCM
- **Chunk Size**: 2,048 bytes (~23.22 ms of audio per packet)
- **Transport**: UDP Datagrams on port `50005`
- **Control Handshake**:
  - `CONNECT`: Sent by Android to register its endpoint with the desktop server.
  - `DISCONNECT`: Sent when the client disconnects to cleanly reset server state.

---

## 6. Building & Testing

### Desktop Suite
```bash
cd desktop
gradle build test
```

### Android Suite
```bash
cd android
./gradlew assembleDebug test
```

---

## 7. Troubleshooting

- **Firewall Prompt on macOS**: When starting the server for the first time, macOS may ask to allow incoming network connections for Java. Click **Allow**.
- **Microphone Permissions**: macOS may prompt for microphone permissions when accessing audio lines. Ensure Terminal / IDE has Microphone permissions enabled in **System Settings ➔ Privacy & Security ➔ Microphone**.
- **Wi-Fi Isolation / AP Isolation**: Ensure both your Mac and Android phone are connected to the same local Wi-Fi network and that router "Client Isolation" / "AP Isolation" is disabled.
