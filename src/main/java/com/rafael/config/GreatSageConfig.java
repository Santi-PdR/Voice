package com.rafael.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GreatSageConfig {
    public static class Server {
        public final ForgeConfigSpec.BooleanValue enableAI;
        public final ForgeConfigSpec.BooleanValue enableGeneratedResponses;
        public final ForgeConfigSpec.BooleanValue enableVoice;
        public final ForgeConfigSpec.ConfigValue<String> ttsProvider;
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
        public final ForgeConfigSpec.BooleanValue announceGamemodeChanges;
        public final ForgeConfigSpec.BooleanValue announceAdvancements;
        public final ForgeConfigSpec.BooleanValue announceItemDrops;

        public Server(ForgeConfigSpec.Builder builder) {
            builder.push("Great Sage / Rafael - Server Native AI and Voice");
            enableAI = builder.comment("Master switch for automatic Rafael reactions. Commands remain available for diagnostics.").define("enableAI", true);
            enableGeneratedResponses = builder.comment("Generate contextual responses with OpenAI from the Forge server. If false or no API key exists, Rafael uses local deterministic responses.").define("enableGeneratedResponses", true);
            enableVoice = builder.comment("Generate synthetic speech on the Forge server and transmit WAV audio directly to clients. No Python/localhost process is required.").define("enableVoice", true);
            ttsProvider = builder.comment("TTS provider: openai or elevenlabs. API keys stay server-side and are never sent to players.").define("ttsProvider", "openai");

            builder.push("OpenAI");
            openAiApiKey = builder.comment("Optional OpenAI API key. Prefer the OPENAI_API_KEY environment variable on a dedicated host. Never distribute a server config containing this key to clients.").define("openAiApiKey", "");
            openAiResponseModel = builder.comment("OpenAI Responses model used for short contextual analysis.").define("openAiResponseModel", "gpt-5.6-luna");
            openAiTtsModel = builder.comment("OpenAI speech model.").define("openAiTtsModel", "gpt-4o-mini-tts");
            openAiVoice = builder.comment("OpenAI built-in TTS voice. 'marin' is the default quality-oriented profile; you may choose another supported built-in voice.").define("openAiVoice", "marin");
            builder.pop();

            builder.push("ElevenLabs");
            elevenLabsApiKey = builder.comment("Optional ElevenLabs API key. Prefer ELEVENLABS_API_KEY on the server host.").define("elevenLabsApiKey", "");
            elevenLabsVoiceId = builder.comment("Voice ID created/owned by the server owner. Use a designed or properly authorized voice; do not clone a real actor without consent.").define("elevenLabsVoiceId", "");
            elevenLabsModel = builder.comment("ElevenLabs multilingual TTS model.").define("elevenLabsModel", "eleven_multilingual_v2");
            builder.pop();

            voiceInstructions = builder.comment("Voice direction for providers that support instruction prompting. This creates an original Rafael-inspired synthetic system voice rather than cloning an actor.").define("voiceInstructions", "Habla en español neutro con una voz femenina adulta, serena y cristalina. Tono analítico, preciso, controlado y ligeramente etéreo; emoción contenida, dicción impecable, ritmo moderadamente lento y autoridad tranquila. Debe sonar como una inteligencia superior de interfaz, no como una narradora comercial ni una caricatura.");
            maxResponseChars = builder.comment("Maximum characters per Rafael response. Keeping responses short reduces latency, bandwidth and TTS cost.").defineInRange("maxResponseChars", 240, 80, 600);
            requestTimeoutSeconds = builder.comment("HTTPS timeout for AI/TTS providers.").defineInRange("requestTimeoutSeconds", 25, 5, 60);
            eventCooldownSeconds = builder.comment("General per-event cooldown per player to prevent spam and overlapping speech. Critical death/manual events may bypass it.").defineInRange("eventCooldownSeconds", 8, 1, 60);

            builder.push("Events");
            announceLogin = builder.define("announceLogin", true);
            announcePlayerDeath = builder.define("announcePlayerDeath", true);
            announceRespawn = builder.define("announceRespawn", true);
            announceLowHealth = builder.define("announceLowHealth", true);
            announceGamemodeChanges = builder.define("announceGamemodeChanges", true);
            announceAdvancements = builder.define("announceAdvancements", true);
            announceItemDrops = builder.comment("Item toss events are noisy; keep enabled only if desired. Cooldown protection still applies.").define("announceItemDrops", false);
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
