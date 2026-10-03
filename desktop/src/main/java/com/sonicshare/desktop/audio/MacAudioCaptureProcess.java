package com.sonicshare.desktop.audio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MacAudioCaptureProcess {

    public static class PermissionDeniedException extends IOException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }

    private final List<String> command;
    private Process process;
    private Thread shutdownHook;
    private volatile boolean permissionDenied = false;

    public MacAudioCaptureProcess() throws FileNotFoundException {
        File binary = findBinary();
        if (binary == null) {
            throw new FileNotFoundException("Native mac-audio-capture binary not found in candidate paths");
        }
        this.command = List.of(binary.getAbsolutePath());
    }

    public MacAudioCaptureProcess(File binaryFile) {
        if (binaryFile == null) {
            throw new IllegalArgumentException("Binary file cannot be null");
        }
        this.command = List.of(binaryFile.getAbsolutePath());
    }

    public MacAudioCaptureProcess(String binaryPath) {
        if (binaryPath == null || binaryPath.isBlank()) {
            throw new IllegalArgumentException("Binary path cannot be null or empty");
        }
        this.command = List.of(binaryPath);
    }

    public MacAudioCaptureProcess(List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        this.command = List.copyOf(command);
    }

    public static File findBinary() {
        String prop = System.getProperty("sonicshare.mac-audio-capture.bin");
        if (prop != null && !prop.isBlank()) {
            File custom = new File(prop);
            if (custom.exists()) {
                return custom;
            }
        }

        String[] candidates = new String[]{
                "desktop/bin/mac-audio-capture",
                "bin/mac-audio-capture",
                "../desktop/bin/mac-audio-capture",
                "./desktop/bin/mac-audio-capture",
                "./bin/mac-audio-capture"
        };

        for (String path : candidates) {
            File candidate = new File(path);
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    public synchronized InputStream start() throws IOException {
        if (process != null && process.isAlive()) {
            return process.getInputStream();
        }

        permissionDenied = false;
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        this.process = pb.start();

        this.shutdownHook = new Thread(this::stop, "MacAudioCaptureProcess-ShutdownHook");
        try {
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }

        try {
            boolean exited = process.waitFor(200, TimeUnit.MILLISECONDS);
            if (exited) {
                int exitCode = process.exitValue();
                if (exitCode == 2) {
                    permissionDenied = true;
                    stop();
                    throw new PermissionDeniedException(
                            "Screen recording permission denied for mac-audio-capture (exit code 2)"
                    );
                } else {
                    stop();
                    throw new IOException(
                            "mac-audio-capture terminated unexpectedly with exit code: " + exitCode
                    );
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
            throw new IOException("Interrupted while waiting for mac-audio-capture initialization", e);
        }

        return process.getInputStream();
    }

    public synchronized void stop() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down
            }
            shutdownHook = null;
        }

        if (process != null) {
            try {
                process.destroy();
                boolean terminated = process.waitFor(500, TimeUnit.MILLISECONDS);
                if (!terminated) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }

            try {
                if (process.getInputStream() != null) {
                    process.getInputStream().close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (process.getOutputStream() != null) {
                    process.getOutputStream().close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (process.getErrorStream() != null) {
                    process.getErrorStream().close();
                }
            } catch (IOException ignored) {
            }
            process = null;
        }
    }

    public synchronized boolean isAlive() {
        return process != null && process.isAlive();
    }

    public boolean isPermissionDenied() {
        if (permissionDenied) {
            return true;
        }
        if (process != null && !process.isAlive()) {
            try {
                if (process.exitValue() == 2) {
                    permissionDenied = true;
                    return true;
                }
            } catch (IllegalThreadStateException ignored) {
            }
        }
        return false;
    }
}
