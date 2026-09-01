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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public final class GreatSageAudioPlayer {
    private static final AtomicReference<Clip> CURRENT_VOICE = new AtomicReference<>();
    private static final ExecutorService VOICE_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Rafael-Client-Audio");
            thread.setDaemon(true);
            return thread;
        }
    });

    private GreatSageAudioPlayer() {}

    public static void playVoice(byte[] wavData) {
        playActivationCue();
        float volume = (float) GreatSageClientConfig.CLIENT.voiceVolume.get().doubleValue();
        if (volume <= 0f || wavData == null || wavData.length < 44) return;
        byte[] immutableAudio = applyVoiceAura(wavData.clone(), GreatSageClientConfig.CLIENT.voiceAuraIntensity.get().floatValue());
        VOICE_EXECUTOR.execute(() -> decodeAndPlay(immutableAudio, volume));
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
            GreatSageMod.LOGGER.info("Voz de Rafael iniciada: {} Hz, {} bit, {} canal(es), ~{} ms",
                    clip.getFormat().getSampleRate(), clip.getFormat().getSampleSizeInBits(),
                    clip.getFormat().getChannels(), clip.getMicrosecondLength() / 1000L);
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn("No se pudo reproducir el WAV recibido de Rafael: {}", e.toString(), e);
        }
    }

    /** Adds a restrained ~34 ms reflection to canonical PCM WAV without changing duration/pitch. */
    private static byte[] applyVoiceAura(byte[] wav, float intensity) {
        if (intensity <= 0.001f || wav.length < 48) return wav;
        try {
            if (wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') return wav;
            int sampleRate = readLe32(wav, 24);
            if (sampleRate < 8000 || sampleRate > 192000) return wav;
            int dataOffset = findDataOffset(wav);
            if (dataOffset < 0 || dataOffset + 2 >= wav.length) return wav;
            int delaySamples = Math.max(1, Math.round(sampleRate * 0.034f));
            int delayBytes = delaySamples * 2;
            int dryScale = 92;
            int wetScale = Math.max(1, Math.round(intensity * 100f));
            for (int p = dataOffset + delayBytes; p + 1 < wav.length; p += 2) {
                int dry = readLe16Signed(wav, p);
                int delayed = readLe16Signed(wav, p - delayBytes);
                int mixed = (dry * dryScale + delayed * wetScale) / 100;
                mixed = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
                wav[p] = (byte) (mixed & 0xFF);
                wav[p + 1] = (byte) ((mixed >>> 8) & 0xFF);
            }
        } catch (Exception ignored) {
            GreatSageMod.LOGGER.debug("No se aplicó aura PCM; se reproduce el WAV original.");
        }
        return wav;
    }

    private static int findDataOffset(byte[] wav) {
        int p = 12;
        while (p + 8 <= wav.length) {
            String id = new String(wav, p, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = readLe32(wav, p + 4);
            if (size < 0) return -1;
            if ("data".equals(id)) return p + 8;
            long next = (long) p + 8L + size + (size & 1);
            if (next > wav.length || next <= p) return -1;
            p = (int) next;
        }
        return -1;
    }

    private static int readLe32(byte[] data, int offset) {
        if (offset < 0 || offset + 3 >= data.length) return -1;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static int readLe16Signed(byte[] data, int offset) {
        int value = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
        return (short) value;
    }

    private static boolean isClipFriendly(AudioFormat format) {
        return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()) && format.getSampleSizeInBits() == 16 && !format.isBigEndian();
    }

    private static void configureVolume(Clip clip, float volume) {
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (20.0 * Math.log10(Math.max(0.0001f, volume)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
        } catch (Exception ignored) {
            GreatSageMod.LOGGER.debug("El mixer del sistema no expone MASTER_GAIN para la voz de Rafael.");
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
            minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.34f * volume, 1.52f);
            minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.24f * volume, 1.75f);
        });
    }

    public static void playTypewriterTick() {
        if (!GreatSageClientConfig.CLIENT.enableTypewriterSound.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        float volume = (float) GreatSageClientConfig.CLIENT.uiSoundVolume.get().doubleValue();
        if (volume <= 0f) return;
        minecraft.execute(() -> {
            if (minecraft.player != null && minecraft.player.level() != null) {
                minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.065f * volume, 2.0f);
            }
        });
    }
}
