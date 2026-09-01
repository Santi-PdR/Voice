package com.rafael.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rafael.network.PacketHandler;
import com.rafael.server.AIEventManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RafaelCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rafael")
                        .then(Commands.literal("test")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String message = StringArgumentType.getString(context, "message");
                                            AIEventManager.triggerAIEvent(player, "Prueba Manual", "Análisis manual ejecutado: " + message);
                                            context.getSource().sendSuccess(() -> Component.literal("§b[Rafael]: §fEvaluando consulta..."), false);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    PacketHandler.sendToClient(player, "Estado del Sistema: Operativo al 100%. Conexión con Gran Sabio estable.", "", "analytical");
                                    context.getSource().sendSuccess(() -> Component.literal("§b[Rafael]: §fEstado enviado al HUD."), false);
                                    return 1;
                                })
                        )
        );
    }
}
