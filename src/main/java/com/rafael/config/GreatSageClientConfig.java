package com.rafael.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class GreatSageClientConfig {
    public static class Client {
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.DoubleValue hudOpacity;
        public final ForgeConfigSpec.IntValue typingSpeedMs;
        public final ForgeConfigSpec.IntValue maxHudLines;
        public final ForgeConfigSpec.DoubleValue voiceVolume;
        public final ForgeConfigSpec.DoubleValue voiceAuraIntensity;
        public final ForgeConfigSpec.DoubleValue voicePresence;
        public final ForgeConfigSpec.DoubleValue uiSoundVolume;
        public final ForgeConfigSpec.BooleanValue enableActivationSound;
        public final ForgeConfigSpec.BooleanValue enableTypewriterSound;
        public final ForgeConfigSpec.BooleanValue showSyntheticVoiceLabel;
        public final ForgeConfigSpec.BooleanValue showLanguageIndicator;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("Great Sage Client Configuration (HUD & Audio)");
            hudScale = builder.comment("Scale of the compact cinematic Raphael HUD.").defineInRange("hudScale", 1.0, 0.65, 1.6);
            hudOpacity = builder.comment("Overall panel opacity without changing text readability.").defineInRange("hudOpacity", 0.94, 0.45, 1.0);
            typingSpeedMs = builder.comment("Typewriter speed in milliseconds per character.").defineInRange("typingSpeedMs", 20, 5, 80);
            maxHudLines = builder.comment("Maximum wrapped response lines.").defineInRange("maxHudLines", 4, 2, 6);
            voiceVolume = builder.comment("Raphael voice volume.").defineInRange("voiceVolume", 1.0, 0.0, 1.0);
            voiceAuraIntensity = builder.comment("Short dual acoustic reflections that create a restrained ethereal/system aura. 0 disables them.").defineInRange("voiceAuraIntensity", 0.11, 0.0, 0.25);
            voicePresence = builder.comment("Subtle PCM presence/clarity emphasis before playback. Kept conservative to avoid metallic distortion.").defineInRange("voicePresence", 0.10, 0.0, 0.30);
            uiSoundVolume = builder.comment("Activation/typewriter sound volume, independent from speech.").defineInRange("uiSoundVolume", 0.36, 0.0, 1.0);
            enableActivationSound = builder.comment("Play Raphael's layered system-link signature when a response arrives.").define("enableActivationSound", true);
            enableTypewriterSound = builder.comment("Play restrained UI ticks during text reveal.").define("enableTypewriterSound", true);
            showSyntheticVoiceLabel = builder.comment("Show LOCAL VOICE / VOZ LOCAL when a generated voice packet is active.").define("showSyntheticVoiceLabel", true);
            showLanguageIndicator = builder.comment("Show ES/EN beside the voice link so automatic language selection is visible.").define("showLanguageIndicator", true);
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

    private GreatSageClientConfig() {}
}
