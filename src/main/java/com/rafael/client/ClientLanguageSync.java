package com.rafael.client;

import com.rafael.GreatSageMod;
import com.rafael.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps the Forge server synchronized with the language selected in the Minecraft client. */
@Mod.EventBusSubscriber(modid = GreatSageMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientLanguageSync {
    private static String lastSentLanguage = "";
    private static int heartbeatTicks = 0;

    private ClientLanguageSync() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            lastSentLanguage = "";
            heartbeatTicks = 0;
            return;
        }

        heartbeatTicks++;
        String language = minecraft.getLanguageManager().getSelected();
        boolean changed = !language.equalsIgnoreCase(lastSentLanguage);
        if (!changed && heartbeatTicks < 200) return;
        heartbeatTicks = 0;

        try {
            PacketHandler.sendLanguageToServer(language);
            lastSentLanguage = language;
        } catch (Exception e) {
            GreatSageMod.LOGGER.debug("Idioma del cliente aún no pudo sincronizarse con Rafael: {}", e.toString());
        }
    }
}
