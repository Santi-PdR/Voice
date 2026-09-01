package com.rafael.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GreatSageConfig {
    public static class Server {
        public final ForgeConfigSpec.BooleanValue enableAI;
        public final ForgeConfigSpec.BooleanValue enableGeneratedResponses;
        public final ForgeConfigSpec.BooleanValue enableVoice;
        public final ForgeConfigSpec.ConfigValue<String> ttsProvider;
        public final ForgeConfigSpec.BooleanValue autoInstallOfflineVoice;
        public final ForgeConfigSpec.BooleanValue prewarmOfflineVoice;
        public final ForgeConfigSpec.DoubleValue offlineLengthScale;
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
        public final ForgeConfigSpec.BooleanValue announceGamemodeChanges;
        public final ForgeConfigSpec.BooleanValue announceDimensionChanges;
        public final ForgeConfigSpec.BooleanValue announceAdvancements;
        public final ForgeConfigSpec.BooleanValue announceItemDrops;

        public Server(ForgeConfigSpec.Builder builder) {
            builder.push("Great Sage / Rafael - Server Native AI and Voice");
            enableAI = builder.comment("Master switch for automatic Rafael reactions. Commands remain available for diagnostics.").define("enableAI", true);
            enableGeneratedResponses = builder.comment("Optional cloud-enhanced contextual responses. Without a server API key Rafael automatically uses the improved local analytical brain; the mod remains fully functional.").define("enableGeneratedResponses", true);
            enableVoice = builder.comment("Enable Rafael speech. The default path is fully local Piper TTS: no API key, account, Python service or persistent terminal is required.").define("enableVoice", true);
            ttsProvider = builder.comment("Voice provider: offline (recommended/default), openai or elevenlabs. If a cloud provider is selected but unavailable, Rafael automatically falls back to offline voice.").define("ttsProvider", "offline");

            builder.push("OfflineVoice");
            autoInstallOfflineVoice = builder.comment("Automatically download the Piper runtime and the open Spanish Daniela high voice on first use. Files are cached and reused afterwards.").define("autoInstallOfflineVoice", true);
            prewarmOfflineVoice = builder.comment("Prepare/download the offline voice asynchronously when the Forge server starts, so the first spoken event does not have to wait for setup.").define("prewarmOfflineVoice", true);
            offlineLengthScale = builder.comment("Piper phoneme length. Values above 1.0 speak more slowly; 1.10 gives Rafael a calm, controlled cadence.").defineInRange("offlineLengthScale", 1.10, 0.75, 1.60);
            offlineNoiseScale = builder.comment("Piper generator variation. Lower values sound more controlled/systematic.").defineInRange("offlineNoiseScale", 0.48, 0.10, 1.20);
            offlineNoiseWidth = builder.comment("Piper phoneme-width variation. Lower values reduce expressive randomness.").defineInRange("offlineNoiseWidth", 0.55, 0.10, 1.20);
            offlineSynthesisTimeoutSeconds = builder.comment("Maximum local TTS synthesis time after the model is ready.").defineInRange("offlineSynthesisTimeoutSeconds", 30, 5, 90);
            builder.pop();

            builder.push("OpenAIOptional");
            openAiApiKey = builder.comment("Optional server-only key for cloud-enhanced analysis/TTS. Not required for normal operation.").define("openAiApiKey", "");
            openAiResponseModel = builder.comment("Optional OpenAI Responses model used for short contextual analysis.").define("openAiResponseModel", "gpt-5.6-luna");
            openAiTtsModel = builder.comment("Optional OpenAI speech model.").define("openAiTtsModel", "gpt-4o-mini-tts");
            openAiVoice = builder.comment("Optional OpenAI built-in TTS voice.").define("openAiVoice", "marin");
            builder.pop();

            builder.push("ElevenLabsOptional");
            elevenLabsApiKey = builder.comment("Optional server-only ElevenLabs API key. Not required for normal operation.").define("elevenLabsApiKey", "");
            elevenLabsVoiceId = builder.comment("Optional authorized/designed Voice ID. Do not clone a real actor without the necessary rights and consent.").define("elevenLabsVoiceId", "");
            elevenLabsModel = builder.comment("Optional ElevenLabs multilingual TTS model.").define("elevenLabsModel", "eleven_multilingual_v2");
            builder.pop();

            voiceInstructions = builder.comment("Direction used only by cloud providers that support prompting. Offline Piper uses the dedicated acoustic tuning above.").define("voiceInstructions", "Habla en español neutro con una voz femenina adulta, serena y cristalina. Tono analítico, preciso, controlado y ligeramente etéreo; emoción contenida, dicción impecable, ritmo moderadamente lento y autoridad tranquila.");
            maxResponseChars = builder.comment("Maximum characters per Rafael response. Short responses reduce latency and keep voice packets compact.").defineInRange("maxResponseChars", 220, 80, 500);
            requestTimeoutSeconds = builder.comment("HTTPS timeout for optional cloud providers and runtime downloads.").defineInRange("requestTimeoutSeconds", 25, 5, 60);
            eventCooldownSeconds = builder.comment("General per-event cooldown per player to prevent spam and overlapping speech.").defineInRange("eventCooldownSeconds", 8, 1, 60);

            builder.push("Events");
            announceLogin = builder.define("announceLogin", true);
            announcePlayerDeath = builder.define("announcePlayerDeath", true);
            announceRespawn = builder.define("announceRespawn", true);
            announceLowHealth = builder.define("announceLowHealth", true);
            announceLowFood = builder.comment("Warn once when hunger crosses into the critical range; cooldown and threshold crossing prevent spam.").define("announceLowFood", true);
            announceGamemodeChanges = builder.define("announceGamemodeChanges", true);
            announceDimensionChanges = builder.comment("Announce dimension transitions such as Overworld -> Nether/End.").define("announceDimensionChanges", true);
            announceAdvancements = builder.define("announceAdvancements", true);
            announceItemDrops = builder.comment("Item toss events are noisy; disabled by default.").define("announceItemDrops", false);
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
}
