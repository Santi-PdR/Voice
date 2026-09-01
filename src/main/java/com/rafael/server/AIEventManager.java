package com.rafael.server;

import com.rafael.GreatSageMod;
import com.rafael.command.RafaelCommand;
import com.rafael.config.GreatSageConfig;
import com.rafael.network.PacketHandler;
import net.minecraft.core.BlockPos;
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
public final class AIEventManager {
    private static final Map<UUID, GameType> PLAYER_GAME_MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PLAYER_FOOD = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PLAYER_AIR = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_LOGIN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REQUEST_SEQUENCE = new ConcurrentHashMap<>();
    private static final Map<String, Long> EVENT_COOLDOWNS = new ConcurrentHashMap<>();

    private AIEventManager() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GreatSageMod.LOGGER.info("Raphael v1.3 server core active. Voice models will prewarm per connected client language.");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RafaelCommand.register(event.getDispatcher());
        GreatSageMod.LOGGER.info("Raphael commands registered.");
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        PLAYER_GAME_MODES.put(id, player.gameMode.getGameModeForPlayer());
        PLAYER_FOOD.put(id, player.getFoodData().getFoodLevel());
        PLAYER_AIR.put(id, player.getAirSupply());
        PENDING_LOGIN.put(id, player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        PLAYER_GAME_MODES.remove(id);
        PLAYER_FOOD.remove(id);
        PLAYER_AIR.remove(id);
        PENDING_LOGIN.remove(id);
        REQUEST_SEQUENCE.remove(id);
        RafaelLanguageManager.remove(id);
        String prefix = id + ":";
        EVENT_COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announcePlayerDeath.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String deathMessage = event.getSource().getLocalizedDeathMessage(player).getString();
        String fallback = tr(player,
                "Alerta crítica. Baja confirmada. Causa registrada: " + deathMessage + ".",
                "Critical alert. Death confirmed. Recorded cause: " + deathMessage + ".");
        triggerAIEvent(player, "DEATH", deathMessage, fallback, true, 0.0f);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();

        Long loginTick = PENDING_LOGIN.get(id);
        if (loginTick != null) {
            long age = player.level().getGameTime() - loginTick;
            if (RafaelLanguageManager.isKnown(id) || age >= 40L) {
                PENDING_LOGIN.remove(id);
                if (GreatSageConfig.SERVER.enableAI.get() && GreatSageConfig.SERVER.announceLogin.get()) {
                    String fallback = tr(player,
                            "Sincronización completada. Usuario " + player.getName().getString() + " reconocido; parámetros del entorno estables.",
                            "Synchronization complete. User " + player.getName().getString() + " recognized; environmental parameters stable.");
                    triggerAIEvent(player, "LOGIN", "Client synchronized", fallback, false);
                }
            }
        }

        GameType current = player.gameMode.getGameModeForPlayer();
        GameType previous = PLAYER_GAME_MODES.put(id, current);
        if (previous != null && previous != current && GreatSageConfig.SERVER.enableAI.get() && GreatSageConfig.SERVER.announceGamemodeChanges.get()) {
            String detail = previous.getName() + " -> " + current.getName();
            String fallback = tr(player,
                    "Parámetros operativos reconfigurados. Modo de juego: " + current.getName() + ".",
                    "Operational parameters reconfigured. Game mode: " + current.getName() + ".");
            triggerAIEvent(player, "GAMEMODE CHANGE", detail, fallback, false);
        }

        int food = player.getFoodData().getFoodLevel();
        Integer previousFood = PLAYER_FOOD.put(id, food);
        if (previousFood != null && previousFood > 6 && food <= 6 && food > 0
                && GreatSageConfig.SERVER.enableAI.get() && GreatSageConfig.SERVER.announceLowFood.get()) {
            String detail = "Food " + food + "/20";
            String fallback = tr(player,
                    "Advertencia metabólica. Reservas de alimento reducidas a " + food + " de 20.",
                    "Metabolic warning. Food reserves reduced to " + food + " of 20.");
            triggerAIEvent(player, "LOW HUNGER", detail, fallback, false);
        }

        int air = player.getAirSupply();
        Integer previousAir = PLAYER_AIR.put(id, air);
        int criticalAir = Math.min(80, Math.max(40, player.getMaxAirSupply() / 4));
        if (previousAir != null && previousAir > criticalAir && air <= criticalAir && air > 0
                && GreatSageConfig.SERVER.enableAI.get() && GreatSageConfig.SERVER.announceLowAir.get()) {
            String fallback = tr(player,
                    "Advertencia respiratoria. Reserva de aire crítica; asciende inmediatamente.",
                    "Respiratory warning. Air reserve critical; surface immediately.");
            triggerAIEvent(player, "LOW AIR", "Air " + air + "/" + player.getMaxAirSupply(), fallback, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.isWasDeath()) return;
        UUID id = player.getUUID();
        PLAYER_GAME_MODES.put(id, player.gameMode.getGameModeForPlayer());
        PLAYER_FOOD.put(id, player.getFoodData().getFoodLevel());
        PLAYER_AIR.put(id, player.getAirSupply());
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceRespawn.get()) return;
        String fallback = tr(player,
                "Regeneración completada. Conciencia y parámetros vitales restaurados.",
                "Regeneration complete. Consciousness and vital parameters restored.");
        triggerAIEvent(player, "RESPAWN", "Respawn after death", fallback, false);
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceDimensionChanges.get()) return;
        String from = event.getFrom().location().toString();
        String to = event.getTo().location().toString();
        String fallback = tr(player,
                "Transición dimensional confirmada. Destino: " + cleanDimension(to) + ".",
                "Dimensional transition confirmed. Destination: " + cleanDimension(to) + ".");
        triggerAIEvent(player, "DIMENSION CHANGE", from + " -> " + to, fallback, false);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceLowHealth.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float healthBefore = player.getHealth();
        float healthAfter = Math.max(0.0f, healthBefore - event.getAmount());
        if (healthBefore > 4.0f && healthAfter <= 4.0f && healthAfter > 0.0f) {
            String detail = String.format(Locale.ROOT, "Post-damage health %.1f/%.1f", healthAfter, player.getMaxHealth());
            String fallback = tr(player,
                    "Advertencia. Umbral vital crítico: " + String.format(Locale.ROOT, "%.1f", healthAfter) + " puntos. Retirada o curación inmediata recomendada.",
                    "Warning. Critical health threshold: " + String.format(Locale.ROOT, "%.1f", healthAfter) + " points. Immediate healing or withdrawal recommended.");
            triggerAIEvent(player, "LOW HEALTH", detail, fallback, false, healthAfter);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceItemDrops.get()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        String item = event.getEntity().getItem().getHoverName().getString();
        String fallback = tr(player, "Objeto descartado: " + item + ".", "Item discarded: " + item + ".");
        triggerAIEvent(player, "ITEM TOSS", item, fallback, false);
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!GreatSageConfig.SERVER.enableAI.get() || !GreatSageConfig.SERVER.announceAdvancements.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String advancement = event.getAdvancement().getId().toString();
        String name = event.getAdvancement().getId().getPath().replace('_', ' ');
        String fallback = tr(player, "Nuevo hito registrado: " + name + ". Progreso actualizado.", "New milestone registered: " + name + ". Progress updated.");
        triggerAIEvent(player, "ADVANCEMENT", advancement, fallback, false);
    }

    public static void triggerAIEvent(ServerPlayer player, String eventType, String fallbackText) {
        triggerAIEvent(player, eventType, fallbackText, fallbackText, false);
    }

    public static void triggerAIEvent(ServerPlayer player, String eventType, String detail, String fallbackText, boolean bypassCooldown) {
        float health = player == null ? 0.0f : player.getHealth();
        triggerAIEvent(player, eventType, detail, fallbackText, bypassCooldown, health);
    }

    private static void triggerAIEvent(ServerPlayer player, String eventType, String detail, String fallbackText, boolean bypassCooldown, float snapshotHealth) {
        if (player == null || player.getServer() == null) return;
        UUID playerId = player.getUUID();
        if (!bypassCooldown && !acquireCooldown(playerId, eventType)) return;

        long requestId = REQUEST_SEQUENCE.merge(playerId, 1L, Long::sum);
        MinecraftServer server = player.getServer();
        BlockPos pos = player.blockPosition();
        String language = RafaelLanguageManager.get(player);
        RafaelService.EventSnapshot snapshot = new RafaelService.EventSnapshot(
                player.getName().getString(), eventType, detail == null ? "" : detail,
                fallbackText == null ? "" : fallbackText, language,
                Math.max(0.0f, snapshotHealth), player.getMaxHealth(), player.getFoodData().getFoodLevel(),
                player.getArmorValue(), player.experienceLevel, player.level().dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ());

        RafaelService.requestSpeech(snapshot, result -> server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target == null) return;
            long latest = REQUEST_SEQUENCE.getOrDefault(playerId, requestId);
            if (requestId != latest) {
                GreatSageMod.LOGGER.debug("Discarding stale Raphael response {} for {} (latest={})", requestId, snapshot.playerName(), latest);
                return;
            }
            PacketHandler.sendToClient(target, result.text(), result.audioWav(), result.emotion(), result.syntheticVoice(), result.language());
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

    private static String tr(ServerPlayer player, String spanish, String english) {
        return RafaelLanguageManager.isSpanish(RafaelLanguageManager.get(player)) ? spanish : english;
    }

    private static String cleanDimension(String value) {
        if (value == null) return "unknown";
        return value.replace("minecraft:", "").replace('_', ' ');
    }
}
