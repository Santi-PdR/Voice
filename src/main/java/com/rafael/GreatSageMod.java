package com.rafael;

import com.rafael.config.GreatSageClientConfig;
import com.rafael.config.GreatSageConfig;
import com.rafael.network.PacketHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(GreatSageMod.MOD_ID)
public final class GreatSageMod {
    public static final String MOD_ID = "great_sage_voice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public GreatSageMod() {
        LOGGER.info("Initializing Great Sage Voice / Raphael v1.5 (Forge 1.20.1, bilingual character-performance voice architecture)");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GreatSageConfig.SERVER_SPEC, "great_sage_voice-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GreatSageClientConfig.CLIENT_SPEC, "great_sage_voice-client.toml");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            LOGGER.info("Raphael protocol v3 registered. v1.5 prioritizes authorized in-character Great Sage/Raphael references over interview speech; no persistent Python/backend/API key required.");
        });
    }
}
