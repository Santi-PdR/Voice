package com.rafael.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GreatSageClientConfig {
    public static class Client {
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.IntValue typingSpeedMs;
        public final ForgeConfigSpec.DoubleValue voiceVolume;
        public final ForgeConfigSpec.BooleanValue enableTypewriterSound;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("Great Sage Client Configuration (HUD & Audio)");

            hudScale = builder
                    .comment("Escala del HUD cinemático de Rafael en pantalla.")
                    .defineInRange("hudScale", 1.0, 0.5, 2.0);

            typingSpeedMs = builder
                    .comment("Velocidad de escritura efecto máquina de escribir (milisegundos por carácter).")
                    .defineInRange("typingSpeedMs", 25, 5, 100);

            voiceVolume = builder
                    .comment("Volumen general de la voz y efectos de Rafael.")
                    .defineInRange("voiceVolume", 1.0, 0.0, 1.0);

            enableTypewriterSound = builder
                    .comment("Activar efectos de sonido de tecleo cibernético al aparecer texto.")
                    .define("enableTypewriterSound", true);

            builder.pop();
        }
    }

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Client, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = specPair.getLeft();
        CLIENT_SPEC = specPair.getRight();
    }
}
