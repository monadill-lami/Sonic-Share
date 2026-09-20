package com.sonicshare.desktop.audio;

import com.sonicshare.desktop.net.UdpAudioSender;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AudioCaptureEngineTest {

    private static class TestMixerInfo extends Mixer.Info {
        public TestMixerInfo(String name, String description) {
            super(name, "SonicShare", description, "1.0");
        }
    }

    @Test
    public void testAudioDeviceManagerDiscoversMixers() {
        List<Mixer.Info> mixers = AudioDeviceManager.getAvailableInputMixers();
        assertNotNull(mixers);
        // On any desktop OS with sound support, at least 0 or more mixers will be returned without error
    }

    @Test
    public void testAudioDeviceManagerFindBestInputMixer() {
        Mixer.Info best = AudioDeviceManager.findBestInputMixer();
        List<Mixer.Info> mixers = AudioDeviceManager.getAvailableInputMixers();
        if (mixers.isEmpty()) {
            assertNull(best);
        } else {
            assertNotNull(best);
        }
    }

    @Test
    public void testAudioDeviceManagerPrioritizesBlackHole() {
        assertNull(AudioDeviceManager.findBestInputMixer(null));
        assertNull(AudioDeviceManager.findBestInputMixer(new ArrayList<>()));

        Mixer.Info mic = new TestMixerInfo("Built-in Microphone", "Internal Mic");
        Mixer.Info blackhole = new TestMixerInfo("BlackHole 2ch", "Virtual Audio Driver");
        Mixer.Info usbMic = new TestMixerInfo("USB Headset", "External Mic");

        // When BlackHole is present second, it should still be prioritized
        List<Mixer.Info> mixers = List.of(mic, blackhole, usbMic);
        Mixer.Info selected = AudioDeviceManager.findBestInputMixer(mixers);
        assertEquals(blackhole, selected);

        // When BlackHole is absent, the first available mixer is returned
        List<Mixer.Info> withoutBlackhole = List.of(mic, usbMic);
        assertEquals(mic, AudioDeviceManager.findBestInputMixer(withoutBlackhole));
    }

    @Test
    public void testAudioCaptureEngineLifecycle() {
        UdpAudioSender mockSender = new UdpAudioSender(50098);
        AudioCaptureEngine engine = new AudioCaptureEngine(mockSender);
        assertFalse(engine.isCapturing());

        // Stop without start should be a no-op and safe
        assertDoesNotThrow(engine::stopCapture);
        assertFalse(engine.isCapturing());
    }

    @Test
    public void testCalculateRms() {
        // Zero length or null buffer
        assertEquals(0.0f, AudioCaptureEngine.calculateRms(null, 0), 0.001f);
        assertEquals(0.0f, AudioCaptureEngine.calculateRms(new byte[0], 0), 0.001f);
        assertEquals(0.0f, AudioCaptureEngine.calculateRms(new byte[1], 1), 0.001f);

        // Silent buffer
        byte[] silence = new byte[2048];
        assertEquals(0.0f, AudioCaptureEngine.calculateRms(silence, silence.length), 0.001f);

        // Buffer with 16-bit PCM values (little endian)
        // 15000 in little-endian: 0x3A98 -> low byte: 0x98 (signed byte -104), high byte: 0x3A (58)
        byte[] testBuf = new byte[4];
        testBuf[0] = (byte) 0x98;
        testBuf[1] = (byte) 0x3A;
        testBuf[2] = (byte) 0x98;
        testBuf[3] = (byte) 0x3A;
        float rms = AudioCaptureEngine.calculateRms(testBuf, testBuf.length);
        assertEquals(1.0f, rms, 0.05f);

        // Maximum signal should clamp at 1.0f
        byte[] maxBuf = new byte[4];
        maxBuf[0] = (byte) 0xFF;
        maxBuf[1] = (byte) 0x7F; // 32767
        maxBuf[2] = (byte) 0xFF;
        maxBuf[3] = (byte) 0x7F;
        assertEquals(1.0f, AudioCaptureEngine.calculateRms(maxBuf, maxBuf.length), 0.001f);
    }

    @Test
    public void testSetAudioLevelListener() {
        UdpAudioSender mockSender = new UdpAudioSender(50099);
        AudioCaptureEngine engine = new AudioCaptureEngine(mockSender);
        assertDoesNotThrow(() -> engine.setAudioLevelListener(level -> {}));
    }

    @Test
    public void testCaptureLoopWithTargetDataLine() throws Exception {
        UdpAudioSender mockSender = new UdpAudioSender(50100);
        AudioCaptureEngine engine = new AudioCaptureEngine(mockSender);

        AtomicBoolean levelCallbackInvoked = new AtomicBoolean(false);
        engine.setAudioLevelListener(rms -> levelCallbackInvoked.set(true));

        AtomicBoolean isOpen = new AtomicBoolean(true);
        TargetDataLine mockLine = (TargetDataLine) Proxy.newProxyInstance(
                TargetDataLine.class.getClassLoader(),
                new Class<?>[]{TargetDataLine.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("isOpen".equals(methodName) || "isRunning".equals(methodName)) {
                        return isOpen.get();
                    }
                    if ("open".equals(methodName) || "start".equals(methodName)) {
                        isOpen.set(true);
                        return null;
                    }
                    if ("stop".equals(methodName) || "close".equals(methodName)) {
                        isOpen.set(false);
                        return null;
                    }
                    if ("read".equals(methodName)) {
                        byte[] b = (byte[]) args[0];
                        int len = (int) args[2];
                        for (int i = 0; i < len; i++) {
                            b[i] = (byte) 0x10;
                        }
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                        return len;
                    }
                    return null;
                }
        );

        engine.startCapture(mockLine);
        assertTrue(engine.isCapturing());

        // Calling startCapture again while capturing should be a safe no-op
        assertDoesNotThrow(() -> engine.startCapture(mockLine));

        // Allow capture loop to execute
        Thread.sleep(100);

        engine.stopCapture();
        assertFalse(engine.isCapturing());
        assertTrue(levelCallbackInvoked.get());
    }

    @Test
    public void testListenerExceptionDoesNotTerminateCaptureLoop() throws Exception {
        UdpAudioSender mockSender = new UdpAudioSender(50101);
        AudioCaptureEngine engine = new AudioCaptureEngine(mockSender);

        AtomicInteger callCount = new AtomicInteger(0);
        engine.setAudioLevelListener(rms -> {
            callCount.incrementAndGet();
            throw new RuntimeException("Simulated listener failure");
        });

        AtomicBoolean isOpen = new AtomicBoolean(true);
        TargetDataLine mockLine = (TargetDataLine) Proxy.newProxyInstance(
                TargetDataLine.class.getClassLoader(),
                new Class<?>[]{TargetDataLine.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("isOpen".equals(methodName) || "isRunning".equals(methodName)) {
                        return isOpen.get();
                    }
                    if ("open".equals(methodName) || "start".equals(methodName)) {
                        isOpen.set(true);
                        return null;
                    }
                    if ("stop".equals(methodName) || "close".equals(methodName)) {
                        isOpen.set(false);
                        return null;
                    }
                    if ("read".equals(methodName)) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {
                        }
                        return 256;
                    }
                    return null;
                }
        );

        engine.startCapture(mockLine);
        Thread.sleep(100);
        assertTrue(callCount.get() >= 2);
        assertTrue(engine.isCapturing());

        engine.stopCapture();
        assertFalse(engine.isCapturing());
    }
}
