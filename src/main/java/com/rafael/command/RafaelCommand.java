package com.rafael.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rafael.network.PacketHandler;
import com.rafael.server.AIEventManager;
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
                                    String message = StringArgumentType.getString(context, "message").trim();
                                    if (message.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal("§cEscribe un mensaje para Rafael."));
                                        return 0;
                                    }
                                    String fallback = "Consulta recibida. " + message;
                                    AIEventManager.triggerAIEvent(player, "Prueba Manual", message, fallback, false);
                                    context.getSource().sendSuccess(() -> Component.literal("§b[Rafael] §7Analizando con el núcleo local; la voz se genera sin API key."), false);
                                    return 1;
                                })))
                .then(Commands.literal("voice")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String phrase = "Sistema vocal offline operativo. Sincronización acústica completada.";
                            AIEventManager.triggerAIEvent(player, "Prueba de Voz", phrase, phrase, false);
                            context.getSource().sendSuccess(() -> Component.literal("§b[Rafael] §7Preparando síntesis local. La primera ejecución puede descargar el modelo automáticamente."), false);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String status = RafaelService.statusSummary();
                            PacketHandler.sendToClient(player, status, new byte[0], "sync", false);
                            context.getSource().sendSuccess(() -> Component.literal("§b[Rafael] §f" + status), false);
                            return 1;
                        }))
                .then(Commands.literal("prepare")
                        .executes(context -> {
                            RafaelService.prewarmVoice();
                            context.getSource().sendSuccess(() -> Component.literal("§b[Rafael] §7Preparación offline solicitada. Usa /rafael status para ver el estado."), false);
                            return 1;
                        })));
    }
}
