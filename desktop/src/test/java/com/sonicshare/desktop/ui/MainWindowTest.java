package com.sonicshare.desktop.ui;

import com.sonicshare.desktop.audio.AudioFormatConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.Mixer;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MainWindowTest {

    private static class DummyMixerInfo extends Mixer.Info {
        DummyMixerInfo(String name) {
            super(name, "Vendor", "Description", "1.0");
        }
    }

    @Test
    void testMixerItemFormatting() {
        // Short name
        MainWindow.MixerItem shortItem = new MainWindow.MixerItem(new DummyMixerInfo("Built-in Mic"));
        assertEquals("Built-in Mic", shortItem.toString());
        assertNotNull(shortItem.getInfo());

        // Exactly 40 chars
        String exact40 = "1234567890123456789012345678901234567890";
        MainWindow.MixerItem item40 = new MainWindow.MixerItem(new DummyMixerInfo(exact40));
        assertEquals(exact40, item40.toString());

        // Over 40 chars (45 chars)
        String longName = "Virtual BlackHole 16-Channel High-Definition Loopback";
        MainWindow.MixerItem longItem = new MainWindow.MixerItem(new DummyMixerInfo(longName));
        String expectedTruncated = longName.substring(0, 37) + "...";
        assertEquals(40, longItem.toString().length());
        assertEquals(expectedTruncated, longItem.toString());

        // Null info
        MainWindow.MixerItem nullItem = new MainWindow.MixerItem(null);
        assertEquals("Default Audio Device", nullItem.toString());
        assertNull(nullItem.getInfo());
    }

    @Test
    void testMainWindowComponentsInitialization() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Skip UI instantiation in headless environment");

        final MainWindow[] windowHolder = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            windowHolder[0] = new MainWindow();
        });

        MainWindow window = windowHolder[0];
        try {
            assertEquals("Sonic Share — Audio Streamer (Desktop)", window.getTitle());
            assertFalse(window.isResizable());
            assertFalse(window.isServerRunning());
            assertTrue(window.getIpLabel().getText().contains(":" + AudioFormatConfig.DEFAULT_PORT));
            assertEquals("Status: Stopped", window.getStatusLabel().getText());
            assertEquals("Start Audio Server", window.getToggleServerBtn().getText());
            assertNotNull(window.getLevelMeter());
            assertNotNull(window.getDeviceCombo());
            assertNotNull(window.getUdpAudioSender());
            assertNotNull(window.getCaptureEngine());

            // Check Audio Source combo box on macOS
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("mac")) {
                assertTrue(window.getDeviceCombo().getItemCount() >= 1);
                MainWindow.MixerItem selected = (MainWindow.MixerItem) window.getDeviceCombo().getSelectedItem();
                assertNotNull(selected);
                assertEquals(com.sonicshare.desktop.audio.AudioDeviceManager.MACOS_SYSTEM_AUDIO_NAME, selected.toString());
            }

            // Verify ZERO microphones in combo box
            for (int i = 0; i < window.getDeviceCombo().getItemCount(); i++) {
                MainWindow.MixerItem item = window.getDeviceCombo().getItemAt(i);
                assertFalse(com.sonicshare.desktop.audio.AudioDeviceManager.isMicrophoneDevice(item.getInfo()),
                        "Device combo must not contain microphone: " + item);
            }
        } finally {
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    @Test
    void testToggleServerWithPermissionDeniedHandlesGracefully() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Skip UI instantiation in headless environment");

        final boolean[] permissionDialogShown = new boolean[1];
        final MainWindow[] windowHolder = new MainWindow[1];

        SwingUtilities.invokeAndWait(() -> {
            com.sonicshare.desktop.net.UdpAudioSender sender = new com.sonicshare.desktop.net.UdpAudioSender(AudioFormatConfig.DEFAULT_PORT);
            com.sonicshare.desktop.audio.AudioCaptureEngine engine = new com.sonicshare.desktop.audio.AudioCaptureEngine(sender);

            // Mock MacAudioCaptureProcess that throws PermissionDeniedException
            com.sonicshare.desktop.audio.MacAudioCaptureProcess mockProc = new com.sonicshare.desktop.audio.MacAudioCaptureProcess(List.of("sh", "-c", "exit 2"));
            engine.setMacAudioCaptureProcess(mockProc);

            windowHolder[0] = new MainWindow(sender, engine) {
                @Override
                protected void showPermissionGuidanceDialog() {
                    permissionDialogShown[0] = true;
                }
            };
        });

        MainWindow window = windowHolder[0];
        try {
            SwingUtilities.invokeAndWait(window::toggleServer);
            assertTrue(permissionDialogShown[0], "Permission guidance dialog should be triggered on permission denial");
            assertFalse(window.isServerRunning(), "Server should remain stopped on error");
            assertEquals("Start Audio Server", window.getToggleServerBtn().getText());
            assertEquals("Status: Stopped", window.getStatusLabel().getText());
        } finally {
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }
}
