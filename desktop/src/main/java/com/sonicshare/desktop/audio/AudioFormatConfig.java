package com.sonicshare.desktop.audio;

import javax.sound.sampled.AudioFormat;

public class AudioFormatConfig {
    public static final float SAMPLE_RATE = 44100.0f;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1; // Mono
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false; // Little-Endian
    public static final int BUFFER_SIZE = 2048; // ~23.2ms frame size
    public static final int DEFAULT_PORT = 50005;

    public static final String CMD_CONNECT = "CONNECT";
    public static final String CMD_DISCONNECT = "DISCONNECT";

    public static AudioFormat getAudioFormat() {
        return new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );
    }
}
