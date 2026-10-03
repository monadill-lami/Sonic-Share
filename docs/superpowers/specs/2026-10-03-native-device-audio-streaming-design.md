# Native Device Audio Streaming Specification

**Date:** 2026-10-03  
**Status:** Approved

## 1. Overview & Goals
The objective of this project is to eliminate microphone recording from Sonic Share completely and replace it with direct, native device/system audio capture.

### Key Goals
- **Zero Microphone Usage:** Remove all microphone input options, listeners, and fallbacks. The application will never listen to or stream from any microphone.
- **Direct System Audio:** Stream the Mac's device audio (system sound, YouTube, music, movies, etc.) directly to the mobile device.
- **Simultaneous Playback:** Mac laptop speakers and mobile phone can play the same audio concurrently.
- **Independent Mute/Volume:** The mobile device continues playing system audio even when the Mac laptop's speakers are muted or set to volume 0.
- **Driverless Setup on macOS:** Use Apple's **ScreenCaptureKit** framework to capture system audio natively without requiring third-party drivers like BlackHole.

---

## 2. Audio Capture Architecture

### 2.1 Native ScreenCaptureKit Helper (`mac-audio-capture`)
A lightweight native CLI utility written in Swift (`native/mac-audio-capture/main.swift`):
- Uses `SCStream` and `SCStreamConfiguration` from Apple's `ScreenCaptureKit` framework.
- Configured with:
  - `capturesAudio = true`
  - `excludesCurrentProcessAudio = true`
  - Audio output settings targeting 44,100 Hz, 1 channel (mono), 16-bit signed integer PCM.
- Streams raw PCM byte packets directly to `stdout`.
- Captures pre-fader system audio, so muting the laptop speakers does not stop or attenuate audio streamed to the mobile phone.
- Handles `SIGINT` / `SIGTERM` gracefully for clean termination.

### 2.2 Compilation & Build Integration
- Compiles via `/usr/bin/swiftc` into `desktop/bin/mac-audio-capture`.
- Integrated into Gradle via a custom task (`compileMacAudioCapture`) that automatically triggers when building or running on macOS.

### 2.3 Java `AudioCaptureEngine` Updates
- Detects the host operating system:
  - On macOS: Defaults to running the `mac-audio-capture` helper process and consuming raw PCM bytes from its `InputStream`.
  - On Linux: Retains support for PulseAudio/PipeWire monitor streams (loopback only, no microphones).
- Continuously calculates RMS audio levels from the PCM stream to drive the desktop `AudioLevelMeter` VU bar.
- Passes audio chunks directly to `UdpAudioSender` for real-time transmission over UDP.
- Ensures the native helper process is destroyed on `stopCapture()` or JVM shutdown.

---

## 3. Complete Removal of Microphone Inputs

### 3.1 Device Filtering (`AudioDeviceManager`)
- Implements a strict blocklist filter that permanently bans any audio device whose name or description contains:
  - `"mic"`, `"microphone"`, `"internal mic"`, `"built-in mic"`, `"headset"`
- **Removes Fallback-to-Mic**: The previous fallback `return mixers.get(0)` which grabbed the built-in microphone is removed completely.
- If running on macOS, `AudioDeviceManager` returns a virtual device entry:
  - `"macOS System Audio (ScreenCaptureKit)"`
- If no valid loopback/system audio source is found, returns an empty list and prevents starting capture, explicitly alerting the user.

### 3.2 UI Updates (`MainWindow`)
- Renames label from **"Audio Input"** to **"Audio Source"**.
- Displays **"macOS System Audio (ScreenCaptureKit)"** as the selected device.
- Microphones are never populated in the dropdown.
- Subtitle and descriptions updated from "microphone" references to "system audio".

---

## 4. Permissions & Error Handling

### 4.1 macOS Screen Recording Permissions
- ScreenCaptureKit requires macOS "Screen & System Audio Recording" permission.
- If permission is denied or not yet granted:
  - The helper exits with a designated status code or error message.
  - `MainWindow` detects this and displays an informative dialog with instructions and an "Open System Settings" button (`x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture`).
  - Capture gracefully stops without crashing or freezing.

### 4.2 Process Resilience
- Helper process lifetime is tied to `AudioCaptureEngine`.
- Registered `Runtime.getRuntime().addShutdownHook` ensures no orphaned background helper processes.
- Non-blocking I/O loop ensures thread termination when capture is toggled off.

---

## 5. Verification & Testing Strategy
1. **Unit Tests:**
   - Verify `AudioDeviceManager` excludes all microphone devices.
   - Verify `AudioDeviceManager` returns the native system audio entry on macOS.
   - Verify `AudioCaptureEngine` handles process failure and stop cleanly.
2. **Build Verification:**
   - Execute `gradle test` and verify all tests pass.
   - Compile and verify `mac-audio-capture` executable.
3. **End-to-End Functional Test:**
   - Launch desktop app and confirm device dropdown shows macOS System Audio and no microphones.
   - Verify audio streaming with audio playback and verify audio continues when laptop volume is muted.
