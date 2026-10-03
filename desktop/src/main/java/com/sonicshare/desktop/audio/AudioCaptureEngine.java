package com.sonicshare.desktop.audio;

import com.sonicshare.desktop.net.UdpAudioSender;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.io.InputStream;

public class AudioCaptureEngine {

    public interface AudioLevelListener {
        void onLevelChanged(float rmsLevel);
    }

    private final UdpAudioSender udpAudioSender;
    private volatile TargetDataLine targetLine;
    private volatile InputStream inputStream;
    private volatile MacAudioCaptureProcess macProcess;
    private volatile boolean isCapturing = false;
    private Thread captureThread;
    private volatile AudioLevelListener levelListener;

    public AudioCaptureEngine(UdpAudioSender udpAudioSender) {
        this.udpAudioSender = udpAudioSender;
    }

    public void setMacAudioCaptureProcess(MacAudioCaptureProcess macProcess) {
        this.macProcess = macProcess;
    }

    public MacAudioCaptureProcess getMacAudioCaptureProcess() {
        return this.macProcess;
    }

    public void setAudioLevelListener(AudioLevelListener listener) {
        this.levelListener = listener;
    }

    public synchronized void startCapture(Mixer.Info mixerInfo) throws LineUnavailableException, IOException {
        if (isCapturing) {
            return;
        }

        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        if (isMac && (mixerInfo == null || isScreenCaptureKitMixer(mixerInfo))) {
            MacAudioCaptureProcess process = (this.macProcess != null) ? this.macProcess : new MacAudioCaptureProcess();
            this.macProcess = process;
            InputStream in = process.start();
            startCapture(in);
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

        startCapture(line);
    }

    private static boolean isScreenCaptureKitMixer(Mixer.Info info) {
        if (info == null) {
            return false;
        }
        String name = (info.getName() != null ? info.getName() : "").toLowerCase();
        String desc = (info.getDescription() != null ? info.getDescription() : "").toLowerCase();
        return name.contains("screencapturekit")
                || name.contains("system audio")
                || desc.contains("screencapturekit")
                || desc.contains("system audio");
    }

    public synchronized void startCapture(InputStream in) {
        if (isCapturing || in == null) {
            return;
        }

        this.inputStream = in;
        this.isCapturing = true;

        captureThread = new Thread(this::captureLoop, "AudioCapture-Thread");
        captureThread.setPriority(Thread.MAX_PRIORITY);
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public synchronized void startCapture(TargetDataLine line) throws LineUnavailableException {
        if (isCapturing || line == null) {
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
        while (isCapturing) {
            TargetDataLine line = this.targetLine;
            InputStream in = this.inputStream;

            if (!isCapturing || (line == null && in == null)) {
                break;
            }
            if (line != null && !line.isOpen()) {
                break;
            }

            try {
                int bytesRead;
                if (line != null) {
                    bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead < 0) {
                        break;
                    }
                } else {
                    bytesRead = in.readNBytes(buffer, 0, buffer.length);
                    if (bytesRead == 0) {
                        break;
                    }
                    if (bytesRead % 2 != 0) {
                        bytesRead -= 1;
                    }
                }

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
                }
            } catch (Exception e) {
                // Line / stream closed or read error
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
        if (macProcess != null) {
            try {
                macProcess.stop();
            } catch (Exception ignored) {
            }
            macProcess = null;
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Exception ignored) {
            }
            inputStream = null;
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
