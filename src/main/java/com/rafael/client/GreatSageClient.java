package com.rafael.client;

import com.rafael.GreatSageMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GreatSageMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GreatSageClient {
    private GreatSageClient() {}

    public static void handleRafaelSpeech(String text, byte[] audioWav, String emotion, boolean syntheticVoice, String language) {
        GreatSageMod.LOGGER.info("[Raphael/{}]: {}", language, text);
        GreatSageHudOverlay.updateText(text, emotion, syntheticVoice, language);
        GreatSageAudioPlayer.playVoice(audioWav);
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("great_sage_hud", GreatSageHudOverlay.HUD_OVERLAY);
        GreatSageMod.LOGGER.info("HUD cinematográfico bilingüe de Raphael registrado.");
    }
}
