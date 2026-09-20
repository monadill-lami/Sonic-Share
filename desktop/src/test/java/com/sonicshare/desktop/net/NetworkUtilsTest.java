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
