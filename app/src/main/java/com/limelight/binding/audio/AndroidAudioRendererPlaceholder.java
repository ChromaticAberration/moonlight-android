package com.limelight.binding.audio;

import com.limelight.nvstream.av.audio.AudioRenderer;

public class AndroidAudioRendererPlaceholder implements AudioRenderer {

    @Override
    public boolean streamInitialized(int channelCount, int channelMask, int samplesPerFrame, int sampleRate) {
        return true;
    }

    @Override
    public void playDecodedAudio(byte[] audioData, int offset, int length) {}

    @Override
    public void streamClosing() {}

    @Override
    public int getCapabilities() {
        return 0;
    }
}
