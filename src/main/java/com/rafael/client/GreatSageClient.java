package com.rafael.client;

import com.rafael.GreatSageMod;
import com.rafael.config.GreatSageClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GreatSageMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GreatSageClient {

    public static void handleRafaelSpeech(String text, String audioUrl, String emotion) {
        GreatSageMod.LOGGER.info("[Rafael / Gran Sabio]: {}", text);

        // Actualizar HUD visual animado (Núcleo luminoso a la derecha y chat personalizado compacto)
        GreatSageHudOverlay.updateText(text);

        // Reproducir la voz de Rafael (y efectos arcanos de sistema)
        GreatSageAudioPlayer.playVoice(audioUrl);

        // NOTA: Eliminado el mensaje duplicado en el chat normal de Minecraft para mantener la limpieza.
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("great_sage_hud", GreatSageHudOverlay.HUD_OVERLAY);
        GreatSageMod.LOGGER.info("HUD cinemático arcano de Rafael registrado correctamente.");
    }
}
