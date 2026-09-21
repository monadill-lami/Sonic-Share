# Sonic Share 🎵

Wireless audio streaming from your Mac laptop to an Android phone over local Wi-Fi. Turn any Android phone into a real-time, low-latency speaker for laptops with broken speakers.

## Features
- **Ultra-low latency (~25-40ms)**: Raw PCM audio streamed over UDP datagrams without container or transcoding overhead.
- **Pure Java**: Built with Java 17, `javax.sound.sampled`, Swing, and Android SDK without native C++ compilation headaches.
- **Live VU Meters**: Real-time visual audio level feedback on both Mac and Android phone.
- **Auto-saved IP**: Remembers your Mac IP address and port on Android via SharedPreferences.
- **Resilient Streaming**: Auto-detection of network interfaces, client timeout detection, and partial wake locks for continuous background playback.

---

## 1. Mac Audio Routing Setup (BlackHole)
Because macOS restricts applications from recording system/speaker audio directly:

1. Install **BlackHole 2ch** (free, open source virtual audio driver):
   ```bash
   brew install blackhole-2ch
   ```
2. In macOS **System Settings ➔ Sound ➔ Output**, select **BlackHole 2ch**.
   *(Audio from YouTube, Spotify, VLC, and browsers will now route directly to BlackHole).*
3. *(Optional Multi-Output Device)*: To hear audio from your Mac's speakers AND route to BlackHole simultaneously:
   - Open **Audio MIDI Setup** (`/System/Applications/Utilities/Audio MIDI Setup.app`).
   - Click the **`+`** icon in the bottom-left and select **Create Multi-Output Device**.
   - Check both **MacBook Speakers** and **BlackHole 2ch** (enable Drift Correction for BlackHole).
   - In macOS **System Settings ➔ Sound ➔ Output**, select the **Multi-Output Device**.
4. *(Fallback)*: If BlackHole is not installed, Sonic Share will automatically detect and use your **Built-in Microphone** for testing and presentation demos.

---

## 2. Running the Desktop Application (Mac)

### Prerequisites
- JDK 17 or higher
- Gradle (or use the system `gradle` command)

### Run
```bash
cd desktop
gradle run
```
1. The window will open displaying your Mac's Wi-Fi IP and port (e.g. `192.168.1.45:50005`).
2. Select your audio input device (defaults to BlackHole 2ch if available).
3. Click **"Start Audio Server"**.
4. The status will display `Listening for connection...`.

---

## 3. Running the Mobile Application (Android)

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

## 4. Audio Streaming Protocol & Architecture

```
+-------------------------------------------------------------+
|                     macOS Desktop App                       |
|                                                             |
|  [System Audio] -> [BlackHole 2ch / Built-in Mic]           |
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
  - `CONNECT`: Sent by Android to register its endpoint with the Mac server.
  - `DISCONNECT`: Sent when the client disconnects to cleanly reset server state.

---

## 5. Building & Testing

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

## 6. Troubleshooting

- **Firewall Prompt on macOS**: When starting the server for the first time, macOS may ask to allow incoming network connections for Java. Click **Allow**.
- **Microphone Permissions**: macOS may prompt for microphone permissions when accessing audio lines. Ensure Terminal / IDE has Microphone permissions enabled in **System Settings ➔ Privacy & Security ➔ Microphone**.
- **Wi-Fi Isolation / AP Isolation**: Ensure both your Mac and Android phone are connected to the same local Wi-Fi network and that router "Client Isolation" / "AP Isolation" is disabled.
