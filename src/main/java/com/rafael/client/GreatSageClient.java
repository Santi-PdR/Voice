package com.rafael.client;

import com.rafael.GreatSageMod;
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

        // Actualizar HUD visual animado con núcleo luminoso, anillos rúnicos y partículas
        GreatSageHudOverlay.updateText(text);

        // Reproducir la voz de Rafael (y efectos arcanos de sistema)
        GreatSageAudioPlayer.playVoice(audioUrl);

        // Mostrar en el chat del juego
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§b[Gran Sabio (Rafael)]: §f" + text));
            }
        });
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("great_sage_hud", GreatSageHudOverlay.HUD_OVERLAY);
        GreatSageMod.LOGGER.info("HUD cinemático arcano de Rafael registrado correctamente.");
    }
}
