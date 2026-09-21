package com.sonicshare.android.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AudioPlaybackEngine {

    public static final int SAMPLE_RATE = 44100;
    private AudioTrack audioTrack;
    private volatile boolean isPlaying = false;

    public synchronized void start() {
        if (isPlaying) return;

        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelConfig, audioEncoding);
        int bufferSize = Math.max(minBufferSize * 2, 4096);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(audioEncoding)
                .setChannelMask(channelConfig)
                .build();

        audioTrack = new AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        audioTrack.play();
        isPlaying = true;
    }

    public int writeAudio(byte[] data, int offset, int length) {
        if (!isPlaying || audioTrack == null || data == null || offset < 0 || length <= 0 || offset + length > data.length) {
            return 0;
        }
        try {
            return audioTrack.write(data, offset, length);
        } catch (Exception e) {
            return 0;
        }
    }

    public synchronized void stop() {
        isPlaying = false;
        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
