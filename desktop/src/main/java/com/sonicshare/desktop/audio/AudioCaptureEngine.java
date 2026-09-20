package com.sonicshare.desktop.audio;

import com.sonicshare.desktop.net.UdpAudioSender;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class AudioCaptureEngine {

    public interface AudioLevelListener {
        void onLevelChanged(float rmsLevel);
    }

    private final UdpAudioSender udpAudioSender;
    private TargetDataLine targetLine;
    private volatile boolean isCapturing = false;
    private Thread captureThread;
    private volatile AudioLevelListener levelListener;

    public AudioCaptureEngine(UdpAudioSender udpAudioSender) {
        this.udpAudioSender = udpAudioSender;
    }

    public void setAudioLevelListener(AudioLevelListener listener) {
        this.levelListener = listener;
    }

    public synchronized void startCapture(Mixer.Info mixerInfo) throws LineUnavailableException {
        if (isCapturing) {
            return;
        }

        AudioFormat format = AudioFormatConfig.getAudioFormat();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);

        TargetDataLine line;
        if (mixerInfo != null) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            line = (TargetDataLine) mixer.getLine(lineInfo);
        } else {
            line = (TargetDataLine) AudioSystem.getLine(lineInfo);
        }

        line.open(format, AudioFormatConfig.BUFFER_SIZE * 4);
        line.start();

        this.targetLine = line;
        this.isCapturing = true;

        captureThread = new Thread(this::captureLoop, "AudioCapture-Thread");
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public synchronized void startCapture(TargetDataLine line) throws LineUnavailableException {
        if (isCapturing) {
            return;
        }

        AudioFormat format = AudioFormatConfig.getAudioFormat();
        if (!line.isOpen()) {
            line.open(format, AudioFormatConfig.BUFFER_SIZE * 4);
        }
        if (!line.isRunning()) {
            line.start();
        }

        this.targetLine = line;
        this.isCapturing = true;

        captureThread = new Thread(this::captureLoop, "AudioCapture-Thread");
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void captureLoop() {
        byte[] buffer = new byte[AudioFormatConfig.BUFFER_SIZE];
        while (isCapturing && targetLine != null && targetLine.isOpen()) {
            try {
                int bytesRead = targetLine.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    if (udpAudioSender != null) {
                        udpAudioSender.sendChunk(buffer, bytesRead);
                    }

                    AudioLevelListener listener = this.levelListener;
                    if (listener != null) {
                        try {
                            float rms = calculateRms(buffer, bytesRead);
                            listener.onLevelChanged(rms);
                        } catch (Throwable t) {
                            // Protect capture thread from uncaught listener exceptions
                        }
                    }
                } else if (bytesRead < 0) {
                    break;
                }
            } catch (Exception e) {
                // Line closed or read error
                break;
            }
        }
    }

    static float calculateRms(byte[] buffer, int length) {
        if (buffer == null || length <= 0) {
            return 0f;
        }

        long sum = 0;
        int effectiveLength = Math.min(buffer.length, length);
        int samples = effectiveLength / 2;
        if (samples == 0) {
            return 0f;
        }

        for (int i = 0; i < effectiveLength - 1; i += 2) {
            // 16-bit little-endian signed
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += (long) sample * sample;
        }

        double mean = (double) sum / samples;
        double rms = Math.sqrt(mean);
        // Normalize 0..32767 to 0.0..1.0
        return (float) Math.max(0.0, Math.min(1.0, rms / 15000.0));
    }

    public synchronized void stopCapture() {
        isCapturing = false;
        if (targetLine != null) {
            try {
                targetLine.stop();
                targetLine.close();
            } catch (Exception ignored) {
            }
            targetLine = null;
        }
        if (captureThread != null) {
            captureThread.interrupt();
            if (Thread.currentThread() != captureThread) {
                try {
                    captureThread.join(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            captureThread = null;
        }
    }

    public boolean isCapturing() {
        return isCapturing;
    }
}
