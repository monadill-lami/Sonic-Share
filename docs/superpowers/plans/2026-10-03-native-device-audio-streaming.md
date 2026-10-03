# Native Device Audio Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completely eliminate microphone capture and implement direct macOS system/device audio capture via ScreenCaptureKit so laptop and phone play simultaneously even when the laptop is muted.

**Architecture:** A lightweight native Swift CLI tool (`mac-audio-capture`) captures macOS pre-fader system audio using ScreenCaptureKit and pipes 44.1kHz 16-bit mono PCM to `stdout`. Java's `AudioCaptureEngine` reads the PCM stream and sends it via UDP, while `AudioDeviceManager` strictly eliminates all microphone devices and fallbacks.

**Tech Stack:** Java 17, Swing, Swift 5, ScreenCaptureKit, CoreMedia, AVFoundation, Gradle.

**Spec:** [`docs/superpowers/specs/2026-10-03-native-device-audio-streaming-design.md`](file:///Users/lami/Documents/Java%20Projects/Sonic%20Share/docs/superpowers/specs/2026-10-03-native-device-audio-streaming-design.md)

## Global Constraints
- Target Audio Format: 44,100 Hz, 16-bit Signed Integer, Mono, Little-Endian (`AudioFormatConfig.java`).
- Zero Microphone Access: No microphone device may ever be listed, selected, or fallen back to.
- macOS System Audio: Must work driverless on macOS 13+ via ScreenCaptureKit.
- Laptop speaker and phone must be able to play simultaneously; muting laptop must not stop phone playback.

---

### Task 1: Complete Removal of Microphone Inputs in `AudioDeviceManager`

**Files:**
- Create: `desktop/src/test/java/com/sonicshare/desktop/audio/AudioDeviceManagerTest.java`
- Modify: `desktop/src/main/java/com/sonicshare/desktop/audio/AudioDeviceManager.java`

**Interfaces:**
- Produces:
  - `AudioDeviceManager.isMicrophoneDevice(String name)` -> `boolean`
  - `AudioDeviceManager.isSupportedSystemAudioDevice(String name)` -> `boolean`
  - `AudioDeviceManager.getAvailableInputMixers()` -> `List<Mixer.Info>` (filtered with zero mics)
  - `AudioDeviceManager.findBestInputMixer(List<Mixer.Info> mixers)` -> `Mixer.Info` (no mic fallback)

- [ ] **Step 1: Write unit tests for AudioDeviceManager microphone filtering**

Add tests asserting:
1. `isMicrophoneDevice("MacBook Air Microphone")` returns `true`.
2. `isMicrophoneDevice("Internal Microphone")` returns `true`.
3. `isMicrophoneDevice("Headset Mic")` returns `true`.
4. `isMicrophoneDevice("BlackHole 2ch")` returns `false`.
5. `isMicrophoneDevice("Monitor of Built-in Audio")` returns `false`.
6. `findBestInputMixer` returns `null` if the only mixer available is a microphone (NEVER falls back to mic).

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests com.sonicshare.desktop.audio.AudioDeviceManagerTest`
Expected: FAIL (methods not yet implemented / old fallback exists).

- [ ] **Step 3: Implement strict microphone filtering in AudioDeviceManager**

In `AudioDeviceManager.java`:
- Implement `isMicrophoneDevice(String name)`.
- Update `getAvailableInputMixers()` to filter out any mixer matching `isMicrophoneDevice(name)`.
- Update `findBestInputMixer()` to remove `mixers.get(0)` microphone fallback, returning `null` if no system monitor or virtual loopback is present.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle test --tests com.sonicshare.desktop.audio.AudioDeviceManagerTest`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add desktop/src/main/java/com/sonicshare/desktop/audio/AudioDeviceManager.java desktop/src/test/java/com/sonicshare/desktop/audio/AudioDeviceManagerTest.java
git commit -m "feat: eliminate microphone input and fallback in AudioDeviceManager"
```

---

### Task 2: Implement Native ScreenCaptureKit Swift Helper and Build Task

**Files:**
- Create: `native/mac-audio-capture/main.swift`
- Modify: `desktop/build.gradle`

**Interfaces:**
- Produces:
  - Executable `desktop/bin/mac-audio-capture`: CLI tool that outputs 44.1kHz 16-bit mono PCM to `stdout`.
  - Gradle task `compileMacAudioCapture` to automatically compile `main.swift` with `swiftc`.

- [ ] **Step 1: Create `native/mac-audio-capture/main.swift`**

Implement the ScreenCaptureKit capture program:
- Check for `CGPreflightScreenCaptureAccess()` and return exit code 2 if permissions are missing.
- Use `SCShareableContent` to configure `SCStreamConfiguration`:
  - `capturesAudio = true`
  - `excludesCurrentProcessAudio = true`
- Implement `SCStreamOutput` converting captured audio into 44.1kHz 16-bit mono PCM using `AVAudioConverter` and writing bytes to `FileHandle.standardOutput`.
- Catch `SIGINT` and `SIGTERM` to cleanly stop `SCStream`.

- [ ] **Step 2: Add Gradle task to compile the helper on macOS**

In `desktop/build.gradle`:
- Add task `compileMacAudioCapture` executing `swiftc native/mac-audio-capture/main.swift -O -o bin/mac-audio-capture`.
- Make `classes` or `run` depend on `compileMacAudioCapture` when `OperatingSystem.current().isMacOsX()`.

- [ ] **Step 3: Compile and test native binary execution**

Run: `gradle compileMacAudioCapture`
Verify `desktop/bin/mac-audio-capture` exists and is executable.

- [ ] **Step 4: Commit changes**

```bash
git add native/mac-audio-capture/main.swift desktop/build.gradle
git commit -m "feat: add native ScreenCaptureKit audio capture helper and gradle build task"
```

---

### Task 3: Implement `MacAudioCaptureProcess` and Integrate into `AudioCaptureEngine`

**Files:**
- Create: `desktop/src/main/java/com/sonicshare/desktop/audio/MacAudioCaptureProcess.java`
- Modify: `desktop/src/main/java/com/sonicshare/desktop/audio/AudioCaptureEngine.java`
- Modify: `desktop/src/test/java/com/sonicshare/desktop/audio/AudioCaptureEngineTest.java`

**Interfaces:**
- Consumes: `desktop/bin/mac-audio-capture`
- Produces:
  - `MacAudioCaptureProcess.start()` -> `InputStream` (raw PCM stream)
  - `MacAudioCaptureProcess.stop()` -> void
  - `MacAudioCaptureProcess.isPermissionDenied()` -> `boolean`
  - `AudioCaptureEngine.startCapture()` -> starts ScreenCaptureKit process on macOS or TargetDataLine on Linux loopback.

- [ ] **Step 1: Write unit tests for MacAudioCaptureProcess and AudioCaptureEngine**

Add tests verifying:
1. `AudioCaptureEngine` can read from an `InputStream` and compute RMS levels without throwing exceptions.
2. `AudioCaptureEngine.stopCapture()` terminates the underlying stream/process cleanly.

- [ ] **Step 2: Implement `MacAudioCaptureProcess`**

- Spawns `desktop/bin/mac-audio-capture`.
- Provides access to process stdout as `InputStream`.
- Handles process exit codes (e.g. exit code 2 for permission denied).
- Adds JVM shutdown hook to kill process if JVM exits.

- [ ] **Step 3: Update `AudioCaptureEngine` to use `MacAudioCaptureProcess` on macOS**

- Detect if running on macOS (`System.getProperty("os.name").toLowerCase().contains("mac")`).
- When on macOS and no specific TargetDataLine is passed, start capture using `MacAudioCaptureProcess`.
- Continuously read 2048-byte chunks, calculate RMS, and invoke `udpAudioSender.sendChunk()`.

- [ ] **Step 4: Run unit tests**

Run: `gradle test`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add desktop/src/main/java/com/sonicshare/desktop/audio/MacAudioCaptureProcess.java desktop/src/main/java/com/sonicshare/desktop/audio/AudioCaptureEngine.java desktop/src/test/java/com/sonicshare/desktop/audio/AudioCaptureEngineTest.java
git commit -m "feat: integrate ScreenCaptureKit process into AudioCaptureEngine"
```

---

### Task 4: Update Desktop UI (`MainWindow.java`) with Audio Source, Status, and Permission Guidance

**Files:**
- Modify: `desktop/src/main/java/com/sonicshare/desktop/ui/MainWindow.java`
- Modify: `desktop/src/test/java/com/sonicshare/desktop/ui/MainWindowTest.java`

**Interfaces:**
- Consumes: `AudioDeviceManager`, `AudioCaptureEngine`, `MacAudioCaptureProcess`
- Produces: Updated UI displaying "Audio Source" and macOS System Audio, with dialog guidance when Screen Recording permission is required.

- [ ] **Step 1: Write UI unit tests in `MainWindowTest`**

Verify that:
1. Label text is "Audio Source:" instead of "Audio Input:".
2. Device combo box includes "macOS System Audio (ScreenCaptureKit)" on macOS and no microphone devices.

- [ ] **Step 2: Update UI elements and layout in `MainWindow.java`**

- Change "Audio Input" label to "Audio Source".
- On macOS, ensure "macOS System Audio (ScreenCaptureKit)" is the default and displayed item.
- Catch `MacAudioCaptureProcess.PermissionDeniedException` (or exit code 2):
  - Show message dialog with instructions and open macOS System Settings (`x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture`).
- Update subtitles to clarify it streams system/device audio.

- [ ] **Step 3: Run unit tests to verify UI changes**

Run: `gradle test`
Expected: PASS.

- [ ] **Step 4: Commit changes**

```bash
git add desktop/src/main/java/com/sonicshare/desktop/ui/MainWindow.java desktop/src/test/java/com/sonicshare/desktop/ui/MainWindowTest.java
git commit -m "feat: update desktop UI for native device audio capture and permissions"
```

---

### Task 5: End-to-End Verification and Documentation Updates

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run full test suite and verify build**

Run: `gradle test` and `gradle build`
Expected: All tests pass.

- [ ] **Step 2: Update `README.md`**

- Update README to document that Sonic Share now uses native device audio capture via ScreenCaptureKit.
- Explain simultaneous playback on laptop and mobile, and that audio continues streaming when laptop is muted.
- Document granting Screen & System Audio Recording permissions in macOS System Settings.
- Remove outdated references to microphone testing and fallback.

- [ ] **Step 3: Commit documentation**

```bash
git add README.md
git commit -m "docs: update README for native device audio capture and mute support"
```
