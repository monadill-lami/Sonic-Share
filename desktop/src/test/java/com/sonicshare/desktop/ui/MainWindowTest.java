package com.sonicshare.desktop.ui;

import com.sonicshare.desktop.audio.AudioFormatConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.Mixer;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

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
        } finally {
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }
}
