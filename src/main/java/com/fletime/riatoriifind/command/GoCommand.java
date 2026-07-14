package com.fletime.riatoriifind.command;

import com.fletime.riatoriifind.service.Navigator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static com.fletime.riatoriifind.command.CommandUtil.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class GoCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("riatoriifindgo")
                .then(argument("x", IntegerArgumentType.integer())
                        .then(argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                    Navigator.setTarget(x, 64, z);
                                    ctx.getSource().sendFeedback(gray(t("riatoriifind.command.go", x, z)));
                                    return 1;
                                })))
                .executes(ctx -> {
                    if (Navigator.hasTarget()) {
                        Navigator.clear();
                        ctx.getSource().sendFeedback(gray(t("riatoriifind.command.go.cancel")));
                    } else {
                        ctx.getSource().sendError(red(t("riatoriifind.command.go.usage")));
                    }
                    return 1;
                }));
    }
}
