package com.rafael.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class GreatSageConfig {
    public static class Server {
        public final ForgeConfigSpec.BooleanValue enableAI;
        public final ForgeConfigSpec.BooleanValue enableGeneratedResponses;
        public final ForgeConfigSpec.BooleanValue enableVoice;
        public final ForgeConfigSpec.ConfigValue<String> fallbackLanguage;
        public final ForgeConfigSpec.ConfigValue<String> ttsProvider;
        public final ForgeConfigSpec.BooleanValue autoInstallOfflineVoice;
        public final ForgeConfigSpec.BooleanValue prewarmOfflineVoice;
        public final ForgeConfigSpec.BooleanValue preferCustomVoiceModels;
        public final ForgeConfigSpec.DoubleValue offlineLengthScale;
        public final ForgeConfigSpec.DoubleValue offlineEnglishLengthScale;
        public final ForgeConfigSpec.DoubleValue offlineNoiseScale;
        public final ForgeConfigSpec.DoubleValue offlineNoiseWidth;
        public final ForgeConfigSpec.IntValue offlineSynthesisTimeoutSeconds;
        public final ForgeConfigSpec.ConfigValue<String> openAiApiKey;
        public final ForgeConfigSpec.ConfigValue<String> openAiResponseModel;
        public final ForgeConfigSpec.ConfigValue<String> openAiTtsModel;
        public final ForgeConfigSpec.ConfigValue<String> openAiVoice;
        public final ForgeConfigSpec.ConfigValue<String> elevenLabsApiKey;
        public final ForgeConfigSpec.ConfigValue<String> elevenLabsVoiceId;
        public final ForgeConfigSpec.ConfigValue<String> elevenLabsModel;
        public final ForgeConfigSpec.ConfigValue<String> voiceInstructions;
        public final ForgeConfigSpec.IntValue maxResponseChars;
        public final ForgeConfigSpec.IntValue requestTimeoutSeconds;
        public final ForgeConfigSpec.IntValue eventCooldownSeconds;
        public final ForgeConfigSpec.BooleanValue announceLogin;
        public final ForgeConfigSpec.BooleanValue announcePlayerDeath;
        public final ForgeConfigSpec.BooleanValue announceRespawn;
        public final ForgeConfigSpec.BooleanValue announceLowHealth;
        public final ForgeConfigSpec.BooleanValue announceLowFood;
        public final ForgeConfigSpec.BooleanValue announceLowAir;
        public final ForgeConfigSpec.BooleanValue announceGamemodeChanges;
        public final ForgeConfigSpec.BooleanValue announceDimensionChanges;
        public final ForgeConfigSpec.BooleanValue announceAdvancements;
        public final ForgeConfigSpec.BooleanValue announceItemDrops;

        public Server(ForgeConfigSpec.Builder builder) {
            builder.push("Great Sage / Raphael - Server Native AI and Voice");
            enableAI = builder.comment("Master switch for automatic Raphael reactions. Diagnostic commands remain available.").define("enableAI", true);
            enableGeneratedResponses = builder.comment("Optional cloud-enhanced analysis. Without a key Raphael uses the bilingual local analytical core.").define("enableGeneratedResponses", true);
            enableVoice = builder.comment("Enable speech. Default is local Piper TTS with no account/API key/Python/terminal.").define("enableVoice", true);
            fallbackLanguage = builder.comment("Fallback when client language has not synchronized yet. Values beginning with es use Spanish; everything else uses English.").define("fallbackLanguage", "en");
            ttsProvider = builder.comment("Voice provider: offline (recommended/default), openai or elevenlabs. Cloud failure automatically falls back to offline.").define("ttsProvider", "offline");

            builder.push("OfflineVoice");
            autoInstallOfflineVoice = builder.comment("Automatically download Piper and only the high-quality language model actually needed by connected players.").define("autoInstallOfflineVoice", true);
            prewarmOfflineVoice = builder.comment("Prepare a player's voice model asynchronously as soon as the client language is known.").define("prewarmOfflineVoice", true);
            preferCustomVoiceModels = builder.comment("If great_sage_voice/custom_voice/es.onnx or en.onnx plus matching .onnx.json exist, use those local models before built-in profiles. Intended for properly licensed/authorized character voice models.").define("preferCustomVoiceModels", true);
            offlineLengthScale = builder.comment("Spanish Piper length scale. Higher = slower. Tuned for controlled Great Sage cadence.").defineInRange("offlineLengthScale", 1.08, 0.75, 1.60);
            offlineEnglishLengthScale = builder.comment("English Piper length scale. Lessac has a different natural cadence from Daniela, so it is tuned separately.").defineInRange("offlineEnglishLengthScale", 1.05, 0.75, 1.60);
            offlineNoiseScale = builder.comment("Generator variation. Lower values sound more controlled/systematic.").defineInRange("offlineNoiseScale", 0.44, 0.10, 1.20);
            offlineNoiseWidth = builder.comment("Phoneme-width variation. Lower values reduce expressive randomness.").defineInRange("offlineNoiseWidth", 0.50, 0.10, 1.20);
            offlineSynthesisTimeoutSeconds = builder.comment("Maximum local synthesis time after the model is ready.").defineInRange("offlineSynthesisTimeoutSeconds", 30, 5, 90);
            builder.pop();

            builder.push("OptionalCloudEnhancement");
            openAiApiKey = builder.comment("Optional server-only OpenAI key. Never required for normal operation.").define("openAiApiKey", "");
            openAiResponseModel = builder.comment("Optional response model.").define("openAiResponseModel", "gpt-5.6-luna");
            openAiTtsModel = builder.comment("Optional OpenAI speech model.").define("openAiTtsModel", "gpt-4o-mini-tts");
            openAiVoice = builder.comment("Optional OpenAI built-in voice.").define("openAiVoice", "marin");
            elevenLabsApiKey = builder.comment("Optional server-only ElevenLabs key.").define("elevenLabsApiKey", "");
            elevenLabsVoiceId = builder.comment("Optional authorized/designed Voice ID. Do not clone a real performer without rights/consent.").define("elevenLabsVoiceId", "");
            elevenLabsModel = builder.comment("Optional multilingual model.").define("elevenLabsModel", "eleven_multilingual_v2");
            voiceInstructions = builder.comment("Character direction for optional promptable providers. Language is selected dynamically per player.").define("voiceInstructions", "Adult feminine analytical intelligence. Calm, crystalline, exact, emotionally restrained, protective authority, measured cadence, subtle ethereal presence, never theatrical or commercial.");
            builder.pop();

            maxResponseChars = builder.comment("Maximum characters per response. Compact speech reduces latency, overlap and packet size.").defineInRange("maxResponseChars", 200, 80, 420);
            requestTimeoutSeconds = builder.comment("HTTPS timeout for optional providers and first-time downloads.").defineInRange("requestTimeoutSeconds", 25, 5, 60);
            eventCooldownSeconds = builder.comment("Per-player/per-event cooldown used with stale-response suppression.").defineInRange("eventCooldownSeconds", 8, 1, 60);

            builder.push("Events");
            announceLogin = builder.define("announceLogin", true);
            announcePlayerDeath = builder.define("announcePlayerDeath", true);
            announceRespawn = builder.define("announceRespawn", true);
            announceLowHealth = builder.define("announceLowHealth", true);
            announceLowFood = builder.comment("Warn once when hunger crosses into critical range.").define("announceLowFood", true);
            announceLowAir = builder.comment("Warn once when remaining air crosses into a dangerous range while submerged.").define("announceLowAir", true);
            announceGamemodeChanges = builder.define("announceGamemodeChanges", true);
            announceDimensionChanges = builder.define("announceDimensionChanges", true);
            announceAdvancements = builder.define("announceAdvancements", true);
            announceItemDrops = builder.comment("Noisy event; disabled by default.").define("announceItemDrops", false);
            builder.pop();
            builder.pop();
        }
    }

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = specPair.getLeft();
        SERVER_SPEC = specPair.getRight();
    }

    private GreatSageConfig() {}
}
