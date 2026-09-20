package com.sonicshare.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

public class AudioDeviceManager {

    public static List<Mixer.Info> getAvailableInputMixers() {
        List<Mixer.Info> inputMixers = new ArrayList<>();
        AudioFormat format = AudioFormatConfig.getAudioFormat();
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        if (mixerInfos != null) {
            for (Mixer.Info info : mixerInfos) {
                try {
                    Mixer mixer = AudioSystem.getMixer(info);
                    if (mixer.isLineSupported(targetInfo)) {
                        inputMixers.add(info);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return inputMixers;
    }

    public static Mixer.Info findBestInputMixer() {
        return findBestInputMixer(getAvailableInputMixers());
    }

    public static Mixer.Info findBestInputMixer(List<Mixer.Info> mixers) {
        if (mixers == null || mixers.isEmpty()) {
            return null;
        }

        // Prioritize BlackHole virtual audio loopback
        for (Mixer.Info info : mixers) {
            if (info != null && info.getName() != null) {
                String name = info.getName().toLowerCase();
                if (name.contains("blackhole")) {
                    return info;
                }
            }
        }

        // Fallback to first available supported input mixer (e.g. built-in mic)
        return mixers.get(0);
    }
}
