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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GreatSageAudioPlayer {

    private static final Set<Clip> ACTIVE_CLIPS = ConcurrentHashMap.newKeySet();

    public static void playVoice(String audioUrl) {
        Minecraft minecraft = Minecraft.getInstance();
        float volume = (float) GreatSageClientConfig.CLIENT.voiceVolume.get().doubleValue();
        if (volume <= 0f) return;

        minecraft.execute(() -> {
            if (minecraft.player != null && minecraft.player.level() != null) {
                minecraft.player.level().playSound(
                        minecraft.player,
                        minecraft.player.blockPosition(),
                        SoundEvents.ENCHANTMENT_TABLE_USE,
                        SoundSource.PLAYERS,
                        0.8f * volume,
                        1.2f
                );
            }
        });

        if (audioUrl == null || audioUrl.isBlank()) {
            GreatSageMod.LOGGER.debug("Rafael recibió un paquete sin audio_url; se reproduce solo el efecto local.");
            return;
        }

        CompletableFuture.runAsync(() -> streamAndPlay(audioUrl, volume));
    }

    private static void streamAndPlay(String audioUrl, float volume) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(audioUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "audio/wav,audio/x-wav,application/octet-stream;q=0.8,*/*;q=0.1");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("HTTP " + responseCode + " al descargar la voz");
            }

            try (InputStream inputStream = connection.getInputStream();
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                 AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bufferedInputStream)) {

                AudioFormat sourceFormat = sourceStream.getFormat();
                AudioInputStream playableStream = sourceStream;
                AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        sourceFormat.getSampleRate(),
                        16,
                        sourceFormat.getChannels(),
                        sourceFormat.getChannels() * 2,
                        sourceFormat.getSampleRate(),
                        false
                );

                if (!isClipFriendly(sourceFormat) && AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                    playableStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
                    GreatSageMod.LOGGER.info("Convirtiendo audio de Rafael en cliente: {} -> {}", sourceFormat, targetFormat);
                }

                Clip clip = AudioSystem.getClip();
                clip.open(playableStream);
                configureVolume(clip, volume);

                ACTIVE_CLIPS.add(clip);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                        ACTIVE_CLIPS.remove(clip);
                        if (clip.isOpen()) {
                            clip.close();
                        }
                    }
                });

                clip.start();
                GreatSageMod.LOGGER.info(
                        "Reproduciendo voz de Rafael: url={}, formato={}, frames={}, duración≈{} ms",
                        audioUrl,
                        clip.getFormat(),
                        clip.getFrameLength(),
                        clip.getMicrosecondLength() / 1000L
                );
            }
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn(
                    "No se pudo reproducir la voz de Rafael desde '{}': {}",
                    audioUrl,
                    e.toString(),
                    e
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isClipFriendly(AudioFormat format) {
        return AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16
                && !format.isBigEndian();
    }

    private static void configureVolume(Clip clip, float volume) {
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (20.0 * Math.log10(Math.max(0.0001, volume)));
            gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
        } catch (Exception e) {
            GreatSageMod.LOGGER.debug("El mixer de Java no expone MASTER_GAIN; se usará el volumen nativo del clip.");
        }
    }

    public static void playTypewriterTick() {
        Minecraft minecraft = Minecraft.getInstance();
        float volume = (float) GreatSageClientConfig.CLIENT.voiceVolume.get().doubleValue();
        if (volume <= 0f) return;

        minecraft.execute(() -> {
            if (minecraft.player != null && minecraft.player.level() != null) {
                minecraft.player.level().playSound(
                        minecraft.player,
                        minecraft.player.blockPosition(),
                        SoundEvents.NOTE_BLOCK_PLING.get(),
                        SoundSource.PLAYERS,
                        0.15f * volume,
                        2.0f
                );
            }
        });
    }
}
