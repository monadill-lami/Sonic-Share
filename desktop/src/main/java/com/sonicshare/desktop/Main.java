package com.sonicshare.desktop;

import com.sonicshare.desktop.ui.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
