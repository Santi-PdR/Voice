package com.rafael.server;

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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
            // Obtener título del logro de forma segura en Forge 1.20.1
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

            try {
                URL url = new URL(endpointUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                if (!apiKey.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                conn.setDoOutput(true);

                String jsonInputString = String.format(
                        "{\"player\": \"%s\", \"event\": \"%s\", \"health\": %.1f, \"dimension\": \"%s\"}",
                        player.getName().getString(),
                        eventType,
                        player.getHealth(),
                        player.level().dimension().location().toString()
                );

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    GreatSageMod.LOGGER.info("IA del servidor consultada exitosamente para evento: {}", eventType);
                } else {
                    GreatSageMod.LOGGER.warn("El servidor de IA respondió con código {}. Usando análisis estándar de Rafael.", responseCode);
                    PacketHandler.sendToClient(player, defaultFallbackText, "", "analytical");
                }

            } catch (Exception e) {
                GreatSageMod.LOGGER.debug("No se pudo conectar al endpoint de IA del servidor ({}: {}). Usando voz analítica local.", endpointUrl, e.getMessage());
                PacketHandler.sendToClient(player, defaultFallbackText, "", "analytical");
            }
        });
    }
}
