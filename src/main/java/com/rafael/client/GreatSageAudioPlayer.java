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
        byte[] immutableAudio = wavData.clone();
        VOICE_EXECUTOR.execute(() -> decodeAndPlay(immutableAudio, volume));
    }

    private static void decodeAndPlay(byte[] wavData, float volume) {
        stopCurrentVoice();
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(wavData);
             BufferedInputStream buffered = new BufferedInputStream(byteStream);
             AudioInputStream sourceStream = AudioSystem.getAudioInputStream(buffered)) {
            AudioFormat sourceFormat = sourceStream.getFormat();
            AudioInputStream playableStream = sourceStream;
            AudioFormat targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16, Math.max(1, sourceFormat.getChannels()), Math.max(1, sourceFormat.getChannels()) * 2, sourceFormat.getSampleRate(), false);
            if (!isClipFriendly(sourceFormat) && AudioSystem.isConversionSupported(targetFormat, sourceFormat)) playableStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
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
            GreatSageMod.LOGGER.info("Voz sintética de Rafael iniciada: {} Hz, {} bit, {} canal(es), ~{} ms", clip.getFormat().getSampleRate(), clip.getFormat().getSampleSizeInBits(), clip.getFormat().getChannels(), clip.getMicrosecondLength() / 1000L);
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn("No se pudo reproducir el WAV recibido de Rafael: {}", e.toString(), e);
        }
    }

    private static boolean isClipFriendly(AudioFormat format) { return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()) && format.getSampleSizeInBits() == 16 && !format.isBigEndian(); }
    private static void configureVolume(Clip clip, float volume) {
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (20.0 * Math.log10(Math.max(0.0001f, volume)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
        } catch (Exception ignored) { GreatSageMod.LOGGER.debug("El mixer del sistema no expone MASTER_GAIN para la voz de Rafael."); }
    }
    private static void stopCurrentVoice() {
        Clip old = CURRENT_VOICE.getAndSet(null);
        if (old != null) { try { old.stop(); } catch (Exception ignored) {} try { old.close(); } catch (Exception ignored) {} }
    }
    private static void playActivationCue() {
        if (!GreatSageClientConfig.CLIENT.enableActivationSound.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        float volume = (float) GreatSageClientConfig.CLIENT.uiSoundVolume.get().doubleValue();
        if (volume <= 0f) return;
        minecraft.execute(() -> {
            if (minecraft.player != null && minecraft.player.level() != null) minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.55f * volume, 1.35f);
        });
    }
    public static void playTypewriterTick() {
        if (!GreatSageClientConfig.CLIENT.enableTypewriterSound.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        float volume = (float) GreatSageClientConfig.CLIENT.uiSoundVolume.get().doubleValue();
        if (volume <= 0f) return;
        minecraft.execute(() -> {
            if (minecraft.player != null && minecraft.player.level() != null) minecraft.player.level().playSound(minecraft.player, minecraft.player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.08f * volume, 1.95f);
        });
    }
}
