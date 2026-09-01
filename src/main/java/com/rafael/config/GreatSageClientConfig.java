package com.rafael.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GreatSageClientConfig {
    public static class Client {
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.IntValue typingSpeedMs;
        public final ForgeConfigSpec.IntValue maxHudLines;
        public final ForgeConfigSpec.DoubleValue voiceVolume;
        public final ForgeConfigSpec.DoubleValue voiceAuraIntensity;
        public final ForgeConfigSpec.DoubleValue uiSoundVolume;
        public final ForgeConfigSpec.BooleanValue enableActivationSound;
        public final ForgeConfigSpec.BooleanValue enableTypewriterSound;
        public final ForgeConfigSpec.BooleanValue showSyntheticVoiceLabel;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("Great Sage Client Configuration (HUD & Audio)");
            hudScale = builder.comment("Scale of the cinematic Rafael HUD. Layout remains screen-safe at every supported scale.").defineInRange("hudScale", 1.0, 0.65, 1.6);
            typingSpeedMs = builder.comment("Typewriter speed in milliseconds per character.").defineInRange("typingSpeedMs", 22, 5, 80);
            maxHudLines = builder.comment("Maximum wrapped text lines shown in the compact Rafael panel.").defineInRange("maxHudLines", 4, 2, 6);
            voiceVolume = builder.comment("Rafael voice volume.").defineInRange("voiceVolume", 1.0, 0.0, 1.0);
            voiceAuraIntensity = builder.comment("Subtle short acoustic reflection mixed into PCM speech to give Rafael a controlled ethereal/system presence. 0 disables it.").defineInRange("voiceAuraIntensity", 0.10, 0.0, 0.25);
            uiSoundVolume = builder.comment("Activation and typewriter UI sound volume, independent from voice volume.").defineInRange("uiSoundVolume", 0.40, 0.0, 1.0);
            enableActivationSound = builder.comment("Play a layered system activation cue when Rafael responds.").define("enableActivationSound", true);
            enableTypewriterSound = builder.comment("Play restrained UI ticks while text is revealed.").define("enableTypewriterSound", true);
            showSyntheticVoiceLabel = builder.comment("Show a small VOICE LINK indicator while generated speech is active.").define("showSyntheticVoiceLabel", true);
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
