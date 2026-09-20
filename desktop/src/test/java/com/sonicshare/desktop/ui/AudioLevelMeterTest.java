package com.sonicshare.desktop.ui;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AudioLevelMeterTest {

    @Test
    void testInitialLevelAndPreferredSize() {
        AudioLevelMeter meter = new AudioLevelMeter();
        assertEquals(0.0f, meter.getLevel(), 0.0001f);
        assertEquals(new Dimension(260, 18), meter.getPreferredSize());
    }

    @Test
    void testClampingLowerBound() {
        AudioLevelMeter meter = new AudioLevelMeter();
        meter.setLevel(-0.5f);
        assertEquals(0.0f, meter.getLevel(), 0.0001f);
    }

    @Test
    void testClampingUpperBound() {
        AudioLevelMeter meter = new AudioLevelMeter();
        meter.setLevel(1.5f);
        assertEquals(1.0f, meter.getLevel(), 0.0001f);
    }

    @Test
    void testValidLevelWithinRange() {
        AudioLevelMeter meter = new AudioLevelMeter();
        meter.setLevel(0.65f);
        assertEquals(0.65f, meter.getLevel(), 0.0001f);
    }

    @Test
    void testPaintComponentDoesNotThrowForVariousLevels() {
        AudioLevelMeter meter = new AudioLevelMeter();
        meter.setSize(260, 18);

        BufferedImage img = new BufferedImage(260, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        try {
            // Level 0.0 (idle/no audio)
            meter.setLevel(0.0f);
            assertDoesNotThrow(() -> meter.paint(g2));

            // Level in green zone (<= 0.5)
            meter.setLevel(0.3f);
            assertDoesNotThrow(() -> meter.paint(g2));

            // Level in yellow zone (> 0.5 and <= 0.8)
            meter.setLevel(0.65f);
            assertDoesNotThrow(() -> meter.paint(g2));

            // Level in red zone (> 0.8)
            meter.setLevel(0.95f);
            assertDoesNotThrow(() -> meter.paint(g2));
        } finally {
            g2.dispose();
        }
    }

    @Test
    void testPaintComponentWithZeroSizeDoesNotThrow() {
        AudioLevelMeter meter = new AudioLevelMeter();
        meter.setSize(0, 0);

        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        try {
            meter.setLevel(0.5f);
            assertDoesNotThrow(() -> meter.paint(g2));
        } finally {
            g2.dispose();
        }
    }
}
