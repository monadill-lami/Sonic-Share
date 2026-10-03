package com.sonicshare.desktop.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.Mixer;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AudioDeviceManagerTest {

    private static class TestMixerInfo extends Mixer.Info {
        public TestMixerInfo(String name, String description) {
            super(name, "SonicShare", description, "1.0");
        }
    }

    @Test
    public void testIsMicrophoneDeviceWithVariousNames() {
        assertTrue(AudioDeviceManager.isMicrophoneDevice("MacBook Air Microphone"));
        assertTrue(AudioDeviceManager.isMicrophoneDevice("Internal Microphone"));
        assertTrue(AudioDeviceManager.isMicrophoneDevice("Headset Mic"));
        assertTrue(AudioDeviceManager.isMicrophoneDevice("Built-in Mic"));
        assertTrue(AudioDeviceManager.isMicrophoneDevice("USB Headset"));
        assertFalse(AudioDeviceManager.isMicrophoneDevice("BlackHole 2ch"));
        assertFalse(AudioDeviceManager.isMicrophoneDevice("Monitor of Built-in Audio"));
        assertFalse(AudioDeviceManager.isMicrophoneDevice("PulseAudio"));
        assertFalse(AudioDeviceManager.isMicrophoneDevice((String) null));
    }

    @Test
    public void testIsMicrophoneDeviceWithMixerInfo() {
        Mixer.Info micByName = new TestMixerInfo("MacBook Air Microphone", "Audio In");
        Mixer.Info micByDesc = new TestMixerInfo("Input Stream", "Internal Microphone");
        Mixer.Info nonMic = new TestMixerInfo("BlackHole 2ch", "Virtual Audio Driver");

        assertTrue(AudioDeviceManager.isMicrophoneDevice(micByName));
        assertTrue(AudioDeviceManager.isMicrophoneDevice(micByDesc));
        assertFalse(AudioDeviceManager.isMicrophoneDevice(nonMic));
        assertFalse(AudioDeviceManager.isMicrophoneDevice((Mixer.Info) null));
    }

    @Test
    public void testIsSupportedSystemAudioDevice() {
        assertTrue(AudioDeviceManager.isSupportedSystemAudioDevice("BlackHole 2ch"));
        assertTrue(AudioDeviceManager.isSupportedSystemAudioDevice("Monitor of Built-in Audio"));
        assertTrue(AudioDeviceManager.isSupportedSystemAudioDevice("PulseAudio"));
        assertTrue(AudioDeviceManager.isSupportedSystemAudioDevice("PipeWire"));
        assertTrue(AudioDeviceManager.isSupportedSystemAudioDevice("macOS System Audio (ScreenCaptureKit)"));

        assertFalse(AudioDeviceManager.isSupportedSystemAudioDevice("MacBook Air Microphone"));
        assertFalse(AudioDeviceManager.isSupportedSystemAudioDevice("Internal Microphone"));
        assertFalse(AudioDeviceManager.isSupportedSystemAudioDevice((String) null));
        assertFalse(AudioDeviceManager.isSupportedSystemAudioDevice((Mixer.Info) null));
    }

    @Test
    public void testFindBestInputMixerNeverFallsBackToMicrophone() {
        Mixer.Info mic1 = new TestMixerInfo("MacBook Air Microphone", "Internal Mic");
        Mixer.Info mic2 = new TestMixerInfo("Headset Mic", "USB Headset");
        List<Mixer.Info> onlyMics = List.of(mic1, mic2);

        // When only microphones are available, findBestInputMixer MUST return null
        assertNull(AudioDeviceManager.findBestInputMixer(onlyMics));
        assertNull(AudioDeviceManager.findBestInputMixer(Collections.emptyList()));
        assertNull(AudioDeviceManager.findBestInputMixer(null));
    }

    @Test
    public void testFindBestInputMixerPrioritizesLoopbackOverMicrophones() {
        Mixer.Info mic = new TestMixerInfo("MacBook Air Microphone", "Internal Mic");
        Mixer.Info blackhole = new TestMixerInfo("BlackHole 2ch", "Virtual Audio Driver");
        Mixer.Info monitor = new TestMixerInfo("Monitor of Built-in Audio", "PulseAudio Monitor");

        // Prioritizes Monitor
        assertEquals(monitor, AudioDeviceManager.findBestInputMixer(List.of(mic, monitor, blackhole)));

        // Prioritizes BlackHole when Monitor is absent
        assertEquals(blackhole, AudioDeviceManager.findBestInputMixer(List.of(mic, blackhole)));
    }

    @Test
    public void testGetAvailableInputMixersExcludesMicrophones() {
        List<Mixer.Info> available = AudioDeviceManager.getAvailableInputMixers();
        assertNotNull(available);
        for (Mixer.Info info : available) {
            assertFalse(AudioDeviceManager.isMicrophoneDevice(info),
                    "Microphone device must never be returned in getAvailableInputMixers: " + info.getName());
        }
    }
}
