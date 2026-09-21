package com.sonicshare.android.net;

import com.sonicshare.android.audio.AudioPlaybackEngine;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioProtocolConstantsTest {

    @Test
    public void testProtocolDefaults() {
        assertEquals("CONNECT", UdpAudioReceiver.CMD_CONNECT);
        assertEquals("DISCONNECT", UdpAudioReceiver.CMD_DISCONNECT);
        assertEquals(2048, UdpAudioReceiver.BUFFER_SIZE);
        assertEquals(44100, AudioPlaybackEngine.SAMPLE_RATE);
    }
}
