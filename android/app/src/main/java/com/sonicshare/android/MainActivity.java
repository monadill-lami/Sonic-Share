package com.sonicshare.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.sonicshare.android.audio.AudioPlaybackEngine;
import com.sonicshare.android.net.UdpAudioReceiver;

public class MainActivity extends AppCompatActivity implements UdpAudioReceiver.ReceiverListener {

    static final String PREFS_NAME = "SonicSharePrefs";
    static final String KEY_LAST_IP = "last_ip";
    static final String KEY_LAST_PORT = "last_port";

    private EditText ipEditText;
    private EditText portEditText;
    private ProgressBar audioLevelBar;
    private TextView statusTextView;
    private Button toggleButton;

    private AudioPlaybackEngine playbackEngine;
    private UdpAudioReceiver audioReceiver;
    private PowerManager.WakeLock wakeLock;
    private boolean isConnectingOrStreaming = false;
    private volatile boolean isTimedOut = false;

    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || !ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int val = Integer.parseInt(part);
                if (val < 0 || val > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    static boolean isValidPort(String portStr) {
        if (portStr == null || portStr.isEmpty()) {
            return false;
        }
        try {
            int port = Integer.parseInt(portStr);
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    boolean isTimedOut() {
        return isTimedOut;
    }

    void setTimedOut(boolean timedOut) {
        this.isTimedOut = timedOut;
    }

    boolean isConnectingOrStreaming() {
        return isConnectingOrStreaming;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipEditText = findViewById(R.id.ipEditText);
        portEditText = findViewById(R.id.portEditText);
        audioLevelBar = findViewById(R.id.audioLevelBar);
        statusTextView = findViewById(R.id.statusTextView);
        toggleButton = findViewById(R.id.toggleButton);

        playbackEngine = new AudioPlaybackEngine();

        // Restore saved server settings
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedIp = prefs.getString(KEY_LAST_IP, "192.168.1.");
        String savedPort = prefs.getString(KEY_LAST_PORT, "50005");
        ipEditText.setText(savedIp);
        portEditText.setText(savedPort);

        toggleButton.setOnClickListener(v -> toggleConnection());

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SonicShare::StreamLock");
        }
    }

    private void toggleConnection() {
        if (!isConnectingOrStreaming) {
            String ip = ipEditText.getText().toString().trim();
            String portStr = portEditText.getText().toString().trim();

            if (!isValidIp(ip)) {
                Toast.makeText(this, "Please enter a valid IPv4 address", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidPort(portStr)) {
                Toast.makeText(this, "Please enter a valid port number", Toast.LENGTH_SHORT).show();
                return;
            }
            int port = Integer.parseInt(portStr);

            // Save IP for next time
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_IP, ip)
                    .putString(KEY_LAST_PORT, portStr)
                    .apply();

            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(120 * 60 * 1000L /* 2 hours max */);
            }

            audioReceiver = new UdpAudioReceiver(ip, port, playbackEngine, this);
            audioReceiver.start();

            isConnectingOrStreaming = true;
            isTimedOut = false;
            ipEditText.setEnabled(false);
            portEditText.setEnabled(false);
            toggleButton.setText(R.string.btn_stop);
            toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.danger));
            statusTextView.setText(R.string.status_streaming);
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.accent));

        } else {
            stopStreaming();
        }
    }

    private void stopStreaming() {
        if (audioReceiver != null) {
            audioReceiver.stop();
            audioReceiver = null;
        }
        playbackEngine.stop();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        isConnectingOrStreaming = false;
        isTimedOut = false;
        ipEditText.setEnabled(true);
        portEditText.setEnabled(true);
        audioLevelBar.setProgress(0);
        toggleButton.setText(R.string.btn_connect);
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.accent));
        statusTextView.setText(R.string.status_disconnected);
        statusTextView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    @Override
    public void onConnected() {
        isTimedOut = false;
        runOnUiThread(() -> {
            statusTextView.setText(R.string.status_streaming);
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.accent));
        });
    }

    @Override
    public void onAudioPacketReceived(float level) {
        runOnUiThread(() -> {
            if (isTimedOut) {
                isTimedOut = false;
                statusTextView.setText(R.string.status_streaming);
                statusTextView.setTextColor(ContextCompat.getColor(this, R.color.accent));
            }
            int progress = (int) (level * 100);
            audioLevelBar.setProgress(progress);
        });
    }

    @Override
    public void onTimedOut() {
        isTimedOut = true;
        runOnUiThread(() -> {
            statusTextView.setText(R.string.status_timeout);
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            audioLevelBar.setProgress(0);
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            stopStreaming();
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(this::stopStreaming);
    }

    @Override
    protected void onDestroy() {
        stopStreaming();
        super.onDestroy();
    }
}
