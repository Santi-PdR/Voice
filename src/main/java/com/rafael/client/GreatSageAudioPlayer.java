package com.rafael.client;

import com.rafael.GreatSageMod;
import com.rafael.config.GreatSageClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class GreatSageAudioPlayer {

    public static void playVoice(String audioUrl) {
        Minecraft minecraft = Minecraft.getInstance();
        double rawVolume = GreatSageClientConfig.CLIENT.voiceVolume.get();
        float volume = (float) rawVolume;
        if (volume <= 0f) return;

        // Efecto de sonido arcano/mágico inmersivo inmediato de activación del Gran Sabio
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

        // Si hay una URL de audio provista por el servidor de IA, reproducirla de forma asíncrona
        if (audioUrl != null && !audioUrl.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    URL url = new URL(audioUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    try (InputStream inputStream = connection.getInputStream();
                         BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                         AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedInputStream)) {

                        Clip clip = AudioSystem.getClip();
                        clip.open(audioInputStream);
                        
                        // Ajustar volumen del clip si soporta control de ganancia
                        try {
                            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                            float dB = (float) (20.0 * Math.log10(Math.max(0.0001, volume)));
                            gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB)));
                        } catch (Exception ignored) {}

                        clip.start();
                        GreatSageMod.LOGGER.info("Reproduciendo voz de Rafael desde el servidor de IA.");
                    }
                } catch (Exception e) {
                    GreatSageMod.LOGGER.debug("Audio URL no accesible ({}), usando efectos de sistema local.", audioUrl);
                }
            });
        }
    }

    public static void playTypewriterTick() {
        Minecraft minecraft = Minecraft.getInstance();
        double rawVolume = GreatSageClientConfig.CLIENT.voiceVolume.get();
        float volume = (float) rawVolume;
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
