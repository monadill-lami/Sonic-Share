package com.sonicshare.desktop.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class AudioLevelMeter extends JComponent {
    private float level = 0.0f; // 0.0 to 1.0

    public AudioLevelMeter() {
        setPreferredSize(new Dimension(260, 18));
    }

    public void setLevel(float level) {
        this.level = Math.max(0.0f, Math.min(1.0f, level));
        repaint();
    }

    public float getLevel() {
        return level;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background track
            g2.setColor(new Color(230, 230, 230));
            g2.fillRoundRect(0, 0, width, height, 8, 8);

            // Filled level
            int fillWidth = (int) (width * level);
            if (fillWidth > 0) {
                Color barColor = (level > 0.8f) ? new Color(230, 80, 80) :
                                 (level > 0.5f) ? new Color(240, 180, 40) :
                                                  new Color(46, 184, 92);
                g2.setColor(barColor);
                g2.fillRoundRect(0, 0, fillWidth, height, 8, 8);
            }

            // Border
            g2.setColor(new Color(180, 180, 180));
            g2.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);
        } finally {
            g2.dispose();
        }
    }
}
