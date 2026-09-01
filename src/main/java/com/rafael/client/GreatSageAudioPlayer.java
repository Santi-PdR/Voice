package com.rafael.client;

import com.rafael.GreatSageMod;
import com.rafael.config.GreatSageClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public final class GreatSageAudioPlayer {
    private static final AtomicReference<Clip> CURRENT_VOICE = new AtomicReference<>();
    private static final ExecutorService VOICE_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Raphael-Client-Audio");
            thread.setDaemon(true);
            return thread;
        }
    });

    private GreatSageAudioPlayer() {}

    public static void playVoice(byte[] wavData) {
        playActivationCue();
        float volume = (float) GreatSageClientConfig.CLIENT.voiceVolume.get().doubleValue();
        if (volume <= 0f || wavData == null || wavData.length < 44) return;

        float aura = GreatSageClientConfig.CLIENT.voiceAuraIntensity.get().floatValue();
        float presence = GreatSageClientConfig.CLIENT.voicePresence.get().floatValue();
        byte[] processed = applyRaphaelSignature(wavData.clone(), aura, presence);
        VOICE_EXECUTOR.execute(() -> decodeAndPlay(processed, volume));
    }

    private static void decodeAndPlay(byte[] wavData, float volume) {
        stopCurrentVoice();
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(wavData);
             BufferedInputStream buffered = new BufferedInputStream(byteStream);
             AudioInputStream sourceStream = AudioSystem.getAudioInputStream(buffered)) {
            AudioFormat sourceFormat = sourceStream.getFormat();
            AudioInputStream playableStream = sourceStream;
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    Math.max(1, sourceFormat.getChannels()),
                    Math.max(1, sourceFormat.getChannels()) * 2,
                    sourceFormat.getSampleRate(),
                    false);

            if (!isClipFriendly(sourceFormat) && AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                playableStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
            }

            Clip clip = AudioSystem.getClip();
            clip.open(playableStream);
            configureVolume(clip, volume);
            CURRENT_VOICE.set(clip);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                    CURRENT_VOICE.compareAndSet(clip, null);
                    if (clip.isOpen()) clip.close();
                }
            });
            clip.start();
            GreatSageMod.LOGGER.info("Raphael voice started: {} Hz, {} bit, {} channel(s), ~{} ms",
                    clip.getFormat().getSampleRate(), clip.getFormat().getSampleSizeInBits(),
                    clip.getFormat().getChannels(), clip.getMicrosecondLength() / 1000L);
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn("Could not play Raphael WAV: {}", e.toString(), e);
        }
    }

    /**
     * Creates a restrained Great Sage-like system presence without changing pitch or duration:
     * conservative peak normalization, slight transient/presence emphasis and two very short reflections.
     * Only canonical PCM16 little-endian WAV is altered; all other formats pass through untouched.
     */
    private static byte[] applyRaphaelSignature(byte[] wav, float aura, float presence) {
        if (wav.length < 48 || (aura <= 0.001f && presence <= 0.001f)) return wav;
        try {
            WavInfo info = inspectPcm16Wav(wav);
            if (info == null) return wav;

            int frameBytes = info.channels() * 2;
            int sampleCount = info.dataSize() / 2;
            if (sampleCount <= info.channels() * 16) return wav;

            short[] original = new short[sampleCount];
            int peak = 1;
            for (int i = 0; i < sampleCount; i++) {
                short sample = (short) readLe16Unsigned(wav, info.dataOffset() + i * 2);
                original[i] = sample;
                peak = Math.max(peak, Math.abs((int) sample));
            }

            float normalizeGain = Math.min(1.08f, 28600.0f / peak);
            int delayFramesA = Math.max(1, Math.round(info.sampleRate() * 0.027f));
            int delayFramesB = Math.max(delayFramesA + 1, Math.round(info.sampleRate() * 0.051f));
            int delaySamplesA = delayFramesA * info.channels();
            int delaySamplesB = delayFramesB * info.channels();
            int fadeFrames = Math.max(1, Math.round(info.sampleRate() * 0.006f));
            int totalFrames = sampleCount / info.channels();

            short[] result = new short[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                int channel = i % info.channels();
                int previousIndex = i - info.channels();
                float dry = original[i] * normalizeGain;
                float previous = previousIndex >= channel ? original[previousIndex] * normalizeGain : dry;

                // Tiny high-frequency/transient emphasis. Intentionally subtle to avoid metallic TTS artifacts.
                float shaped = dry + (dry - previous) * (presence * 0.22f);
                float reflectedA = i >= delaySamplesA ? original[i - delaySamplesA] * normalizeGain : 0.0f;
                float reflectedB = i >= delaySamplesB ? original[i - delaySamplesB] * normalizeGain : 0.0f;
                float mixed = shaped * (1.0f - aura * 0.28f)
                        + reflectedA * aura
                        + reflectedB * aura * 0.43f;

                int frame = i / info.channels();
                float envelope = 1.0f;
                if (frame < fadeFrames) envelope = frame / (float) fadeFrames;
                else if (frame >= totalFrames - fadeFrames) envelope = Math.max(0.0f, (totalFrames - 1 - frame) / (float) fadeFrames);
                mixed *= envelope;
                result[i] = clamp16(Math.round(mixed));
            }

            for (int i = 0; i < sampleCount; i++) {
                int offset = info.dataOffset() + i * 2;
                int value = result[i];
                wav[offset] = (byte) (value & 0xFF);
                wav[offset + 1] = (byte) ((value >>> 8) & 0xFF);
            }
            return wav;
        } catch (Exception e) {
            GreatSageMod.LOGGER.debug("Raphael voice signature skipped; original WAV retained: {}", e.toString());
            return wav;
        }
    }

    private static WavInfo inspectPcm16Wav(byte[] wav) {
        if (wav.length < 44 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F'
                || wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') return null;
        int p = 12;
        int format = -1;
        int channels = -1;
        int sampleRate = -1;
        int bits = -1;
        int dataOffset = -1;
        int dataSize = -1;

        while (p + 8 <= wav.length) {
            String id = new String(wav, p, 4, StandardCharsets.US_ASCII);
            int size = readLe32(wav, p + 4);
            if (size < 0 || (long) p + 8L + size > wav.length) return null;
            if ("fmt ".equals(id) && size >= 16) {
                format = readLe16Unsigned(wav, p + 8);
                channels = readLe16Unsigned(wav, p + 10);
                sampleRate = readLe32(wav, p + 12);
                bits = readLe16Unsigned(wav, p + 22);
            } else if ("data".equals(id)) {
                dataOffset = p + 8;
                dataSize = size;
                break;
            }
            p += 8 + size + (size & 1);
        }

        if (format != 1 || channels < 1 || channels > 2 || sampleRate < 8000 || sampleRate > 192000
                || bits != 16 || dataOffset < 0 || dataSize < 2 || dataOffset + dataSize > wav.length) return null;
        return new WavInfo(sampleRate, channels, dataOffset, dataSize);
    }

    private static int readLe32(byte[] data, int offset) {
        if (offset < 0 || offset + 3 >= data.length) return -1;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int readLe16Unsigned(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static short clamp16(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private static boolean isClipFriendly(AudioFormat format) {
        return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16 && !format.isBigEndian();
    }

    private static void configureVolume(Clip clip, float volume) {
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (20.0 * Math.log10(Math.max(0.0001f, volume)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
        } catch (Exception ignored) {
            GreatSageMod.LOGGER.debug("System mixer does not expose MASTER_GAIN for Raphael voice.");
        }
    }

    private static void stopCurrentVoice() {
        Clip old = CURRENT_VOICE.getAndSet(null);
        if (old != null) {
            try { old.stop(); } catch (Exception ignored) {}
            try { old.close(); } catch (Exception ignored) {}
        }
    }

    private static void playActivationCue() {
        if (!GreatSageClientConfig.CLIENT.enableActivationSound.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        float volume = (float) GreatSageClientConfig.CLIENT.uiSoundVolume.get().doubleValue();
        if (volume <= 0f) return;
        minecraft.execute(() -> {
            if (minecraft.player == null || minecraft.player.level() == null) return;
            minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.28f * volume, 1.58f);
            minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.22f * volume, 1.82f);
        });
    }

    public static void playTypewriterTick() {
        if (!GreatSageClientConfig.CLIENT.enableTypewriterSound.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        float volume = (float) GreatSageClientConfig.CLIENT.uiSoundVolume.get().doubleValue();
        if (volume <= 0f) return;
        minecraft.execute(() -> {
            if (minecraft.player != null && minecraft.player.level() != null) {
                minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.055f * volume, 2.0f);
            }
        });
    }

    private record WavInfo(int sampleRate, int channels, int dataOffset, int dataSize) {}
}
