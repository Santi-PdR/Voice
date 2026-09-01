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
public class GreatSageMod {
    public static final String MOD_ID = "great_sage_voice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public GreatSageMod() {
        LOGGER.info("Inicializando Great Sage Voice / Rafael (Forge 1.20.1, arquitectura server-native)");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GreatSageConfig.SERVER_SPEC, "great_sage_voice-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GreatSageClientConfig.CLIENT_SPEC, "great_sage_voice-client.toml");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            LOGGER.info("Canal Rafael v2 registrado: texto + WAV server->client; Python/localhost no requerido.");
        });
    }
}
