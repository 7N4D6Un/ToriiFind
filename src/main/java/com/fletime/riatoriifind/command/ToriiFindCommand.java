package com.fletime.riatoriifind.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static com.fletime.riatoriifind.command.CommandUtil.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class ToriiFindCommand {

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			registerCommands(dispatcher)
		);
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("riatoriifind")
			.executes(ToriiFindCommand::showHelp)
			.then(argument("query", StringArgumentType.greedyString())
				.executes(ctx -> FindCommand.searchFind(ctx, StringArgumentType.getString(ctx, "query").trim()))));
	}

	private static int showHelp(CommandContext<FabricClientCommandSource> ctx) {
		var src = ctx.getSource();
		src.sendFeedback(gray(t("riatoriifind.command.help.find")));
		return 1;
	}
}
