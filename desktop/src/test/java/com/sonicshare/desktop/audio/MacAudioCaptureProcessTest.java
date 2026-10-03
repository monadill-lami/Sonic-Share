package com.sonicshare.desktop.audio;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MacAudioCaptureProcessTest {

    @Test
    public void testBinaryResolution() {
        File binary = MacAudioCaptureProcess.findBinary();
        assertNotNull(binary, "Native mac-audio-capture binary should be discovered");
        assertTrue(binary.exists(), "Binary file must exist");
        assertTrue(binary.canExecute(), "Binary file must be executable");
    }

    @Test
    public void testPermissionDeniedThrowsPermissionDeniedException() {
        // Exit code 2 simulates ScreenCaptureAccessDenied
        MacAudioCaptureProcess process = new MacAudioCaptureProcess(List.of("sh", "-c", "exit 2"));
        assertFalse(process.isPermissionDenied());
        assertFalse(process.isAlive());

        MacAudioCaptureProcess.PermissionDeniedException ex = assertThrows(
                MacAudioCaptureProcess.PermissionDeniedException.class,
                process::start
        );
        assertNotNull(ex.getMessage());
        assertTrue(process.isPermissionDenied());
        assertFalse(process.isAlive());
    }

    @Test
    public void testPrematureExitThrowsIOException() {
        // Exit code 1 simulates general failure
        MacAudioCaptureProcess process = new MacAudioCaptureProcess(List.of("sh", "-c", "exit 1"));

        IOException ex = assertThrows(IOException.class, process::start);
        assertFalse(ex instanceof MacAudioCaptureProcess.PermissionDeniedException);
        assertFalse(process.isPermissionDenied());
        assertFalse(process.isAlive());
    }

    @Test
    public void testStartReadAndStopLifecycle() throws Exception {
        // Generates continuous zero bytes to simulate a stream
        MacAudioCaptureProcess process = new MacAudioCaptureProcess(List.of("cat", "/dev/zero"));
        InputStream in = process.start();
        assertNotNull(in);
        assertTrue(process.isAlive());

        byte[] buf = new byte[2048];
        int bytesRead = in.read(buf, 0, buf.length);
        assertEquals(2048, bytesRead);

        process.stop();
        assertFalse(process.isAlive());
    }

    @Test
    public void testStopWhenNotStartedIsSafe() {
        MacAudioCaptureProcess process = new MacAudioCaptureProcess(List.of("sh", "-c", "exit 0"));
        assertDoesNotThrow(process::stop);
        assertFalse(process.isAlive());
    }

    @Test
    public void testDefaultConstructorDiscoversBinary() {
        assertDoesNotThrow(() -> {
            MacAudioCaptureProcess process = new MacAudioCaptureProcess();
            assertFalse(process.isAlive());
            assertFalse(process.isPermissionDenied());
        });
    }
}
