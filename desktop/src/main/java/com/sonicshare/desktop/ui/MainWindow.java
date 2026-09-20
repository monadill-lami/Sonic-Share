package com.sonicshare.desktop.ui;

import com.sonicshare.desktop.audio.AudioCaptureEngine;
import com.sonicshare.desktop.audio.AudioDeviceManager;
import com.sonicshare.desktop.audio.AudioFormatConfig;
import com.sonicshare.desktop.net.NetworkUtils;
import com.sonicshare.desktop.net.UdpAudioSender;

import javax.sound.sampled.Mixer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.util.List;

public class MainWindow extends JFrame {

    private final UdpAudioSender udpAudioSender;
    private final AudioCaptureEngine captureEngine;

    private JLabel ipLabel;
    private JComboBox<MixerItem> deviceCombo;
    private AudioLevelMeter levelMeter;
    private JLabel statusLabel;
    private JButton toggleServerBtn;
    private boolean isServerRunning = false;

    public static class MixerItem {
        private final Mixer.Info info;

        public MixerItem(Mixer.Info info) {
            this.info = info;
        }

        public Mixer.Info getInfo() {
            return info;
        }

        @Override
        public String toString() {
            if (info == null || info.getName() == null) {
                return "Default Audio Device";
            }
            String name = info.getName();
            return name.length() > 40 ? name.substring(0, 37) + "..." : name;
        }
    }

    public MainWindow() {
        this(new UdpAudioSender(AudioFormatConfig.DEFAULT_PORT), null);
    }

    public MainWindow(UdpAudioSender sender, AudioCaptureEngine engine) {
        super("Sonic Share — Audio Streamer (Desktop)");
        this.udpAudioSender = sender != null ? sender : new UdpAudioSender(AudioFormatConfig.DEFAULT_PORT);
        this.captureEngine = engine != null ? engine : new AudioCaptureEngine(this.udpAudioSender);

        initUI();
        setupListeners();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(480, 380);
        setLocationRelativeTo(null);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isServerRunning) {
                    captureEngine.stopCapture();
                    udpAudioSender.stopListening();
                }
            }
        });

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 24, 20, 24));
        mainPanel.setBackground(Color.WHITE);

        // Header Title
        JLabel titleLabel = new JLabel("Sonic Share");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Stream Mac audio to your Android phone");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(subtitleLabel);
        mainPanel.add(Box.createVerticalStrut(18));

        // IP Card
        JPanel ipCard = new JPanel(new GridLayout(2, 1, 4, 4));
        ipCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));
        ipCard.setBackground(new Color(248, 249, 250));
        ipCard.setMaximumSize(new Dimension(440, 70));

        JLabel ipTitle = new JLabel("YOUR MAC WI-FI IP (ENTER THIS ON PHONE):");
        ipTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        ipTitle.setForeground(new Color(100, 100, 100));

        String localIp = NetworkUtils.getLocalIPv4Address();
        ipLabel = new JLabel(localIp + ":" + AudioFormatConfig.DEFAULT_PORT);
        ipLabel.setFont(new Font("Monospaced", Font.BOLD, 18));
        ipLabel.setForeground(new Color(30, 100, 220));

        ipCard.add(ipTitle);
        ipCard.add(ipLabel);
        mainPanel.add(ipCard);
        mainPanel.add(Box.createVerticalStrut(14));

        // Audio Input Selector
        JPanel devicePanel = new JPanel(new BorderLayout(8, 0));
        devicePanel.setBackground(Color.WHITE);
        devicePanel.setMaximumSize(new Dimension(440, 30));
        JLabel devLabel = new JLabel("Audio Input:");
        devLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        deviceCombo = new JComboBox<>();
        populateAudioDevices();
        devicePanel.add(devLabel, BorderLayout.WEST);
        devicePanel.add(deviceCombo, BorderLayout.CENTER);
        mainPanel.add(devicePanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Live VU Meter
        JPanel meterPanel = new JPanel(new BorderLayout(8, 0));
        meterPanel.setBackground(Color.WHITE);
        meterPanel.setMaximumSize(new Dimension(440, 24));
        JLabel meterLabel = new JLabel("Live Input:");
        meterLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        levelMeter = new AudioLevelMeter();
        meterPanel.add(meterLabel, BorderLayout.WEST);
        meterPanel.add(levelMeter, BorderLayout.CENTER);
        mainPanel.add(meterPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Status Banner
        statusLabel = new JLabel("Status: Stopped", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(new Color(120, 120, 120));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createVerticalStrut(16));

        // Start / Stop Button
        toggleServerBtn = new JButton("Start Audio Server");
        toggleServerBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        toggleServerBtn.setPreferredSize(new Dimension(220, 42));
        toggleServerBtn.setMaximumSize(new Dimension(220, 42));
        toggleServerBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        toggleServerBtn.setBackground(new Color(46, 184, 92));
        toggleServerBtn.setForeground(Color.WHITE);
        toggleServerBtn.setFocusPainted(false);
        toggleServerBtn.setOpaque(true);
        toggleServerBtn.addActionListener(e -> toggleServer());
        mainPanel.add(toggleServerBtn);

        setContentPane(mainPanel);
    }

    private void populateAudioDevices() {
        List<Mixer.Info> mixers = AudioDeviceManager.getAvailableInputMixers();
        Mixer.Info best = AudioDeviceManager.findBestInputMixer(mixers);
        MixerItem selectedItem = null;

        for (Mixer.Info m : mixers) {
            MixerItem item = new MixerItem(m);
            deviceCombo.addItem(item);
            if (best != null && m.getName().equals(best.getName())) {
                selectedItem = item;
            }
        }
        if (selectedItem != null) {
            deviceCombo.setSelectedItem(selectedItem);
        }
    }

    private void setupListeners() {
        captureEngine.setAudioLevelListener(rms -> SwingUtilities.invokeLater(() -> levelMeter.setLevel(rms)));

        udpAudioSender.setConnectionListener(new UdpAudioSender.ConnectionListener() {
            @Override
            public void onConnected(InetAddress client, int port) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Status: Streaming to " + client.getHostAddress() + ":" + port);
                    statusLabel.setForeground(new Color(46, 184, 92));
                });
            }

            @Override
            public void onDisconnected() {
                SwingUtilities.invokeLater(() -> {
                    if (isServerRunning) {
                        statusLabel.setText("Status: Waiting for Android Phone...");
                        statusLabel.setForeground(new Color(230, 150, 20));
                    }
                });
            }
        });
    }

    public void toggleServer() {
        if (!isServerRunning) {
            try {
                udpAudioSender.startListening();
                MixerItem selected = (MixerItem) deviceCombo.getSelectedItem();
                captureEngine.startCapture(selected != null ? selected.getInfo() : null);

                isServerRunning = true;
                toggleServerBtn.setText("Stop Server");
                toggleServerBtn.setBackground(new Color(220, 53, 69));
                statusLabel.setText("Status: Waiting for Android Phone...");
                statusLabel.setForeground(new Color(230, 150, 20));
                deviceCombo.setEnabled(false);
            } catch (Exception ex) {
                udpAudioSender.stopListening();
                JOptionPane.showMessageDialog(this, "Failed to start server: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            captureEngine.stopCapture();
            udpAudioSender.stopListening();
            isServerRunning = false;
            levelMeter.setLevel(0);
            toggleServerBtn.setText("Start Audio Server");
            toggleServerBtn.setBackground(new Color(46, 184, 92));
            statusLabel.setText("Status: Stopped");
            statusLabel.setForeground(new Color(120, 120, 120));
            deviceCombo.setEnabled(true);
        }
    }

    public boolean isServerRunning() {
        return isServerRunning;
    }

    public JLabel getStatusLabel() {
        return statusLabel;
    }

    public JButton getToggleServerBtn() {
        return toggleServerBtn;
    }

    public JComboBox<MixerItem> getDeviceCombo() {
        return deviceCombo;
    }

    public AudioLevelMeter getLevelMeter() {
        return levelMeter;
    }

    public JLabel getIpLabel() {
        return ipLabel;
    }

    public UdpAudioSender getUdpAudioSender() {
        return udpAudioSender;
    }

    public AudioCaptureEngine getCaptureEngine() {
        return captureEngine;
    }
}
