package com.rafael.server;

import com.rafael.GreatSageMod;
import com.rafael.command.RafaelCommand;
import com.rafael.config.GreatSageConfig;
import com.rafael.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = GreatSageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AIEventManager {
    private static final Map<UUID, GameType> PLAYER_GAME_MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PLAYER_FOOD = new ConcurrentHashMap<>();
    private static final Map<String, Long> EVENT_COOLDOWNS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RafaelService.prewarmVoice();
        GreatSageMod.LOGGER.info("Rafael server-native activo. Voz offline se preparará automáticamente si está habilitada.");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RafaelCommand.register(event.getDispatcher());
        GreatSageMod.LOGGER.info("Comandos de Rafael (/rafael) registrados.");
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PLAYER_GAME_MODES.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
        PLAYER_FOOD.put(player.getUUID(), player.getFoodData().getFoodLevel());
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceLogin.get()) return;
        String text = "Sincronización completada. Usuario " + player.getName().getString() + " reconocido; parámetros del entorno estables.";
        triggerAIEvent(player, "Conexión de Jugador", text, text, false);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PLAYER_GAME_MODES.remove(player.getUUID());
        PLAYER_FOOD.remove(player.getUUID());
        String prefix = player.getUUID() + ":";
        EVENT_COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announcePlayerDeath.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            String deathMessage = event.getSource().getLocalizedDeathMessage(player).getString();
            String fallback = "Alerta crítica. Baja confirmada. Causa registrada: " + deathMessage + ".";
            triggerAIEvent(player, "Muerte de Jugador", deathMessage, fallback, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();

        GameType current = player.gameMode.getGameModeForPlayer();
        GameType previous = PLAYER_GAME_MODES.put(id, current);
        if (previous != null && previous != current && GreatSageConfig.SERVER.enableAI.get() && GreatSageConfig.SERVER.announceGamemodeChanges.get()) {
            String detail = previous.getName() + " -> " + current.getName();
            String fallback = "Parámetros operativos reconfigurados. Modo de juego establecido en " + current.getName() + ".";
            triggerAIEvent(player, "Cambio de Modo de Juego", detail, fallback, false);
        }

        int food = player.getFoodData().getFoodLevel();
        Integer previousFood = PLAYER_FOOD.put(id, food);
        if (previousFood != null && previousFood > 6 && food <= 6 && food > 0
                && GreatSageConfig.SERVER.enableAI.get() && GreatSageConfig.SERVER.announceLowFood.get()) {
            String detail = "Nivel de alimento: " + food + "/20";
            String fallback = "Advertencia metabólica. Reservas de alimento reducidas a " + food + " de 20. Reposición recomendada.";
            triggerAIEvent(player, "Hambre Crítica", detail, fallback, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.isWasDeath()) return;
        PLAYER_GAME_MODES.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
        PLAYER_FOOD.put(player.getUUID(), player.getFoodData().getFoodLevel());
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceRespawn.get()) return;
        String fallback = "Regeneración biológica completada. Conciencia y parámetros vitales restaurados.";
        triggerAIEvent(player, "Reaparición / Respawn", "Respawn tras muerte", fallback, false);
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceDimensionChanges.get()) return;
        String from = event.getFrom().location().toString();
        String to = event.getTo().location().toString();
        String detail = from + " -> " + to;
        String fallback = "Transición dimensional confirmada. Destino registrado: " + to.replace("minecraft:", "").replace('_', ' ') + ".";
        triggerAIEvent(player, "Cambio de Dimensión", detail, fallback, false);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceLowHealth.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float healthBefore = player.getHealth();
        float healthAfter = Math.max(0.0f, healthBefore - event.getAmount());
        if (healthBefore > 4.0f && healthAfter <= 4.0f && healthAfter > 0.0f) {
            String detail = String.format(Locale.ROOT, "Salud estimada tras daño: %.1f/20", healthAfter);
            String fallback = "Advertencia. Umbral vital crítico detectado: " + String.format(Locale.ROOT, "%.1f", healthAfter) + " puntos. Retirada o curación inmediata recomendada.";
            triggerAIEvent(player, "Salud Crítica", detail, fallback, false);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceItemDrops.get()) return;
        if (event.getPlayer() instanceof ServerPlayer player) {
            String item = event.getEntity().getItem().getHoverName().getString();
            String fallback = "Objeto descartado del inventario activo: " + item + ".";
            triggerAIEvent(player, "Objeto Descartado", item, fallback, false);
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceAdvancements.get()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            String advancement = event.getAdvancement().getId().toString();
            String fallback = "Nuevo hito registrado: " + event.getAdvancement().getId().getPath().replace('_', ' ') + ". Progreso actualizado.";
            triggerAIEvent(player, "Logro Obtenido", advancement, fallback, false);
        }
    }

    public static void triggerAIEvent(ServerPlayer player, String eventType, String fallbackText) {
        triggerAIEvent(player, eventType, fallbackText, fallbackText, "Muerte de Jugador".equalsIgnoreCase(eventType));
    }

    public static void triggerAIEvent(ServerPlayer player, String eventType, String detail, String fallbackText, boolean bypassCooldown) {
        if (player == null || player.getServer() == null) return;
        if (!bypassCooldown && !acquireCooldown(player.getUUID(), eventType)) return;
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUUID();
        RafaelService.EventSnapshot snapshot = new RafaelService.EventSnapshot(
                player.getName().getString(), eventType, detail == null ? "" : detail,
                fallbackText == null ? "" : fallbackText, player.getHealth(),
                player.level().dimension().location().toString());

        RafaelService.requestSpeech(snapshot, result -> server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target == null) {
                GreatSageMod.LOGGER.debug("Respuesta de Rafael descartada: el jugador {} ya no está conectado.", snapshot.playerName());
                return;
            }
            PacketHandler.sendToClient(target, result.text(), result.audioWav(), result.emotion(), result.syntheticVoice());
        }));
    }

    private static boolean acquireCooldown(UUID playerId, String eventType) {
        int cooldownSeconds = GreatSageConfig.SERVER.eventCooldownSeconds.get();
        String key = playerId + ":" + eventType;
        long now = System.currentTimeMillis();
        Long previous = EVENT_COOLDOWNS.get(key);
        if (previous != null && now - previous < cooldownSeconds * 1000L) return false;
        EVENT_COOLDOWNS.put(key, now);
        return true;
    }
}
