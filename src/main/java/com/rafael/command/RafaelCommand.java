package com.rafael.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rafael.network.PacketHandler;
import com.rafael.server.AIEventManager;
import com.rafael.server.RafaelLanguageManager;
import com.rafael.server.RafaelService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RafaelCommand {
    private RafaelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rafael")
                .then(Commands.literal("test")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String language = RafaelLanguageManager.get(player);
                                    boolean es = RafaelLanguageManager.isSpanish(language);
                                    String message = StringArgumentType.getString(context, "message").trim();
                                    if (message.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal(es ? "§cEscribe un mensaje para Rafael." : "§cEnter a message for Raphael."));
                                        return 0;
                                    }
                                    AIEventManager.triggerAIEvent(player, "MANUAL TEST", message, "", true);
                                    context.getSource().sendSuccess(() -> Component.literal(es
                                            ? "§b[Rafael] §7Analizando telemetría en español…"
                                            : "§b[Raphael] §7Analyzing telemetry in English…"), false);
                                    return 1;
                                })))
                .then(Commands.literal("voice")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String language = RafaelLanguageManager.get(player);
                            boolean es = RafaelLanguageManager.isSpanish(language);
                            AIEventManager.triggerAIEvent(player, "VOICE TEST", "Offline neural voice test", "", true);
                            context.getSource().sendSuccess(() -> Component.literal(es
                                    ? "§b[Rafael] §7Prueba de voz neural local solicitada."
                                    : "§b[Raphael] §7Local neural voice test requested."), false);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String language = RafaelLanguageManager.get(player);
                            String status = RafaelService.statusSummary(language);
                            PacketHandler.sendToClient(player, status, new byte[0], "sync", false, language);
                            context.getSource().sendSuccess(() -> Component.literal("§b[Raphael] §f" + status), false);
                            return 1;
                        }))
                .then(Commands.literal("prepare")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String language = RafaelLanguageManager.get(player);
                            boolean es = RafaelLanguageManager.isSpanish(language);
                            RafaelService.prewarmVoice(language);
                            context.getSource().sendSuccess(() -> Component.literal(es
                                    ? "§b[Rafael] §7Preparación de la voz española solicitada. Usa /rafael status."
                                    : "§b[Raphael] §7English voice preparation requested. Use /rafael status."), false);
                            return 1;
                        }))
                .then(Commands.literal("language")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String language = RafaelLanguageManager.get(player);
                            boolean es = RafaelLanguageManager.isSpanish(language);
                            context.getSource().sendSuccess(() -> Component.literal(es
                                    ? "§b[Rafael] §fIdioma detectado: Español. La voz y respuestas se adaptan automáticamente."
                                    : "§b[Raphael] §fDetected language: English. Voice and responses adapt automatically."), false);
                            return 1;
                        })));
    }
}
