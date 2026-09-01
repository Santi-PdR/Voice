package com.rafael.server;

import com.rafael.GreatSageMod;
import com.rafael.config.GreatSageConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks each connected client's Minecraft language without exposing client config or secrets. */
public final class RafaelLanguageManager {
    public static final String SPANISH = "es";
    public static final String ENGLISH = "en";

    private static final Map<UUID, String> PLAYER_LANGUAGES = new ConcurrentHashMap<>();

    private RafaelLanguageManager() {}

    public static void update(ServerPlayer player, String minecraftLanguage) {
        if (player == null) return;
        String normalized = normalize(minecraftLanguage);
        String previous = PLAYER_LANGUAGES.put(player.getUUID(), normalized);
        if (!normalized.equals(previous)) {
            GreatSageMod.LOGGER.info("Idioma de Rafael sincronizado para {}: {} -> {}", player.getName().getString(), minecraftLanguage, normalized);
            RafaelService.prewarmVoice(normalized);
        }
    }

    public static String get(ServerPlayer player) {
        if (player == null) return fallback();
        return PLAYER_LANGUAGES.getOrDefault(player.getUUID(), fallback());
    }

    public static String get(UUID playerId) {
        return playerId == null ? fallback() : PLAYER_LANGUAGES.getOrDefault(playerId, fallback());
    }

    public static boolean isKnown(UUID playerId) {
        return playerId != null && PLAYER_LANGUAGES.containsKey(playerId);
    }

    public static void remove(UUID playerId) {
        if (playerId != null) PLAYER_LANGUAGES.remove(playerId);
    }

    public static boolean isSpanish(String language) {
        return SPANISH.equals(normalize(language));
    }

    public static String normalize(String language) {
        if (language == null || language.isBlank()) return fallback();
        String value = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return value.startsWith("es") ? SPANISH : ENGLISH;
    }

    public static String displayName(String language) {
        return isSpanish(language) ? "Español" : "English";
    }

    private static String fallback() {
        String configured = GreatSageConfig.SERVER.fallbackLanguage.get();
        if (configured == null || configured.isBlank()) return ENGLISH;
        String value = configured.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("es") ? SPANISH : ENGLISH;
    }
}
