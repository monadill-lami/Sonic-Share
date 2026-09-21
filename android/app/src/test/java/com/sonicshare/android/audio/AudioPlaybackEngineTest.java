package com.sonicshare.android.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioPlaybackEngineTest {

    @Test
    public void testInitialStateNotPlaying() {
        AudioPlaybackEngine engine = new AudioPlaybackEngine();
        assertFalse(engine.isPlaying());
    }

    @Test
    public void testWriteAudioWhenNotPlayingReturnsZero() {
        AudioPlaybackEngine engine = new AudioPlaybackEngine();
        byte[] data = new byte[2048];
        int written = engine.writeAudio(data, 0, data.length);
        assertEquals(0, written);
    }

    @Test
    public void testWriteAudioNullOrInvalidBoundsReturnsZero() {
        AudioPlaybackEngine engine = new AudioPlaybackEngine();
        assertEquals(0, engine.writeAudio(null, 0, 100));
        assertEquals(0, engine.writeAudio(new byte[10], -1, 5));
        assertEquals(0, engine.writeAudio(new byte[10], 0, -1));
        assertEquals(0, engine.writeAudio(new byte[10], 5, 10)); // out of bounds
    }

    @Test
    public void testStopWhenNotPlayingIsSafe() {
        AudioPlaybackEngine engine = new AudioPlaybackEngine();
        engine.stop();
        assertFalse(engine.isPlaying());
    }
}
