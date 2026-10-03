package com.sonicshare.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

public class AudioDeviceManager {

    /**
     * Checks if the device name indicates a microphone device.
     * Permanently blocks devices containing "mic", "microphone", "headset", etc.
     */
    public static boolean isMicrophoneDevice(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("mic") || lower.contains("headset");
    }

    /**
     * Checks if the mixer info represents a microphone device by checking both name and description.
     */
    public static boolean isMicrophoneDevice(Mixer.Info info) {
        if (info == null) {
            return false;
        }
        return isMicrophoneDevice(info.getName()) || isMicrophoneDevice(info.getDescription());
    }

    /**
     * Checks if the given audio device name corresponds to a supported system audio source
     * (e.g. ScreenCaptureKit virtual device, PulseAudio/PipeWire monitor loopback, or BlackHole).
     */
    public static boolean isSupportedSystemAudioDevice(String name) {
        if (name == null || isMicrophoneDevice(name)) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("monitor")
                || lower.contains("blackhole")
                || lower.contains("screencapturekit")
                || lower.contains("system audio")
                || lower.contains("pulse")
                || lower.contains("pipewire");
    }

    /**
     * Checks if the given audio device corresponds to a supported system audio source.
     */
    public static boolean isSupportedSystemAudioDevice(Mixer.Info info) {
        if (info == null) {
            return false;
        }
        return isSupportedSystemAudioDevice(info.getName()) || isSupportedSystemAudioDevice(info.getDescription());
    }

    public static List<Mixer.Info> getAvailableInputMixers() {
        List<Mixer.Info> inputMixers = new ArrayList<>();
        AudioFormat format = AudioFormatConfig.getAudioFormat();
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        if (mixerInfos != null) {
            for (Mixer.Info info : mixerInfos) {
                // Permanently exclude any microphone device or non-system audio source
                if (isMicrophoneDevice(info) || !isSupportedSystemAudioDevice(info)) {
                    continue;
                }
                try {
                    Mixer mixer = AudioSystem.getMixer(info);
                    if (mixer.isLineSupported(targetInfo)) {
                        inputMixers.add(info);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return inputMixers;
    }

    public static Mixer.Info findBestInputMixer() {
        return findBestInputMixer(getAvailableInputMixers());
    }

    public static Mixer.Info findBestInputMixer(List<Mixer.Info> mixers) {
        if (mixers == null || mixers.isEmpty()) {
            return null;
        }

        // 1. Prioritize Linux PulseAudio/PipeWire monitor loopback streams
        for (Mixer.Info info : mixers) {
            if (info != null && !isMicrophoneDevice(info) && info.getName() != null) {
                String name = info.getName().toLowerCase();
                if (name.contains("monitor")) {
                    return info;
                }
            }
        }

        // 2. Prioritize macOS BlackHole virtual audio loopback
        for (Mixer.Info info : mixers) {
            if (info != null && !isMicrophoneDevice(info) && info.getName() != null) {
                String name = info.getName().toLowerCase();
                if (name.contains("blackhole")) {
                    return info;
                }
            }
        }

        // 3. Secondary priority for Linux PulseAudio/PipeWire default mixers
        for (Mixer.Info info : mixers) {
            if (info != null && !isMicrophoneDevice(info) && info.getName() != null) {
                String name = info.getName().toLowerCase();
                if (name.contains("pulse") || name.contains("pipewire")) {
                    return info;
                }
            }
        }

        // Zero microphone fallback: Never fall back to microphones or unknown input devices.
        // Returns null if no system monitor or virtual loopback is present.
        return null;
    }
}
