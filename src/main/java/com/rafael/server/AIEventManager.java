package com.rafael.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rafael.GreatSageMod;
import com.rafael.command.RafaelCommand;
import com.rafael.config.GreatSageConfig;
import com.rafael.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = GreatSageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AIEventManager {

    private static final Map<UUID, GameType> playerGameModes = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RafaelCommand.register(event.getDispatcher());
        GreatSageMod.LOGGER.info("Comandos de Rafael (/rafael) registrados correctamente.");
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            playerGameModes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
            String defaultText = "Análisis completado. Usuario " + player.getName().getString() + " sincronizado correctamente en el sistema.";
            triggerAIEvent(player, "Conexión de Jugador", defaultText);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            playerGameModes.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announcePlayerDeath.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            String deathMsg = event.getSource().getLocalizedDeathMessage(player).getString();
            String defaultText = "Alerta crítica. El objetivo " + player.getName().getString() + " ha sufrido una baja médica irreversible. Causa: " + deathMsg + ".";
            triggerAIEvent(player, "Muerte de Jugador", defaultText);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get()) return;
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            GameType currentMode = player.gameMode.getGameModeForPlayer();
            UUID uuid = player.getUUID();
            GameType previousMode = playerGameModes.get(uuid);

            if (previousMode == null) {
                playerGameModes.put(uuid, currentMode);
            } else if (previousMode != currentMode) {
                playerGameModes.put(uuid, currentMode);
                String defaultText = "Información: Cambio de modo de juego detectado a [" + currentMode.name() + "]. Recalibrando parámetros cinemáticos.";
                GreatSageMod.LOGGER.info("Rafael detectó cambio de gamemode para {}: de {} a {}", player.getName().getString(), previousMode, currentMode);
                triggerAIEvent(player, "Cambio de Modo de Juego", defaultText);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!GreatSageConfig.SERVER.enableAI.get()) return;
        if (event.getEntity() instanceof ServerPlayer player && event.isWasDeath()) {
            playerGameModes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
            String defaultText = "Análisis completado. Regeneración biológica y restauración de consciencia exitosa para " + player.getName().getString() + ".";
            triggerAIEvent(player, "Reaparición / Respawn", defaultText);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceLowHealth.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            float healthAfter = player.getHealth() - event.getAmount();
            if (healthAfter <= 4.0f && player.getHealth() > 4.0f) {
                String defaultText = "Advertencia: Salud crítica detectada (" + String.format("%.1f", healthAfter) + "/20.0). Se recomienda retirada táctica inmediata o consumo de poción.";
                triggerAIEvent(player, "Salud Crítica", defaultText);
            }
        }
    }

    @SubscribeEvent
    public static void onItemSelectedOrDropped(ItemTossEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceItemDrops.get()) return;
        if (event.getPlayer() instanceof ServerPlayer player) {
            String itemName = event.getEntity().getItem().getHoverName().getString();
            String defaultText = "Información: Se ha descartado el objeto [" + itemName + "] del inventario activo.";
            triggerAIEvent(player, "Objeto Droppeado", defaultText);
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            String defaultText = "Notificación: Se ha registrado un nuevo hito / avance en el progreso del usuario. Capacidad analítica expandida.";
            try {
                String advName = event.getAdvancement().getId().getPath();
                defaultText = "Notificación: Hito completado [" + advName.toUpperCase() + "]. Sintonización de skill mejorada.";
            } catch (Exception ignored) {}
            triggerAIEvent(player, "Logro Obtenido", defaultText);
        }
    }

    public static void triggerAIEvent(ServerPlayer player, String eventType, String defaultFallbackText) {
        CompletableFuture.runAsync(() -> {
            String endpointUrl = GreatSageConfig.SERVER.aiEndpointUrl.get();
            String apiKey = GreatSageConfig.SERVER.apiKey.get();
            HttpURLConnection conn = null;

            try {
                URL url = new URL(endpointUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(30000);
                if (!apiKey.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                conn.setDoOutput(true);

                JsonObject requestJson = new JsonObject();
                requestJson.addProperty("player", player.getName().getString());
                requestJson.addProperty("event", eventType);
                requestJson.addProperty("detail", defaultFallbackText);
                requestJson.addProperty("health", player.getHealth());
                requestJson.addProperty("dimension", player.level().dimension().location().toString());

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestJson.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    String responseBody = readBody(conn.getInputStream());
                    JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

                    String text = getString(responseJson, "text", defaultFallbackText);
                    String audioUrl = getString(responseJson, "audio_url", "");
                    String emotion = getString(responseJson, "emotion", "analytical");
                    String audioStatus = getString(responseJson, "audio_status", audioUrl.isEmpty() ? "missing" : "ready");
                    String audioError = getString(responseJson, "audio_error", "");

                    PacketHandler.sendToClient(player, text, audioUrl, emotion);
                    GreatSageMod.LOGGER.info(
                            "IA consultada para '{}': texto={} chars, audioStatus={}, audioUrl={}",
                            eventType, text.length(), audioStatus, audioUrl.isEmpty() ? "<vacía>" : audioUrl
                    );
                    if (audioUrl.isEmpty() && !audioError.isEmpty()) {
                        GreatSageMod.LOGGER.warn("Backend respondió sin voz para '{}': {}", eventType, audioError);
                    }
                } else {
                    String errorBody = conn.getErrorStream() != null ? readBody(conn.getErrorStream()) : "";
                    GreatSageMod.LOGGER.warn(
                            "El servidor de IA respondió HTTP {} para '{}'. Respuesta: {}. Usando fallback local.",
                            responseCode, eventType, errorBody
                    );
                    PacketHandler.sendToClient(player, defaultFallbackText, "", "analytical");
                }
            } catch (Exception e) {
                GreatSageMod.LOGGER.warn(
                        "Falló la consulta a Rafael en {} para '{}': {}. Usando fallback local.",
                        endpointUrl, eventType, e.toString(), e
                );
                PacketHandler.sendToClient(player, defaultFallbackText, "", "analytical");
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static String readBody(InputStream stream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static String getString(JsonObject json, String key, String fallback) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try {
                String value = json.get(key).getAsString();
                return value != null ? value : fallback;
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
