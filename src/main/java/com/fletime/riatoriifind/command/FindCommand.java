package com.fletime.riatoriifind.command;

import com.fletime.riatoriifind.RiaToriiFind;
import com.fletime.riatoriifind.config.ModConfig;
import com.fletime.riatoriifind.service.ToriiDataService;
import com.fletime.riatoriifind.service.ToriiDataService.FindEntry;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.List;

import static com.fletime.riatoriifind.command.CommandUtil.*;

public class FindCommand {

	public static int searchFind(CommandContext<FabricClientCommandSource> ctx, String query) {
		if (ModConfig.debugMode) RiaToriiFind.LOGGER.info("搜索请求: \"{}\"", query);
		try {
			var results = ToriiDataService.searchAll(query);
			displayFindResults(ctx, results);
		} catch (Exception e) {
			ctx.getSource().sendError(red(t("riatoriifind.error.load_data", e.getMessage())));
		}
		return 1;
	}

	private static void displayFindResults(CommandContext<FabricClientCommandSource> ctx, List<FindEntry> results) {
		var src = ctx.getSource();
		if (results.isEmpty()) {
			src.sendFeedback(divider());
			src.sendFeedback(red(t("riatoriifind.result.empty.find")));
			src.sendFeedback(divider());
			return;
		}

		int maxId = 2, maxGrade = 2;
		for (var e : results) {
			maxId = Math.max(maxId, displayWidth(e.id()));
			maxGrade = Math.max(maxGrade, displayWidth(e.grade()));
		}

		src.sendFeedback(divider());
		var countStr = String.valueOf(results.size());
		var prefix = Component.literal(t("riatoriifind.result.title.prefix").getString()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		var before = Component.literal(t("riatoriifind.result.title.suffix.before").getString()).withStyle(ChatFormatting.GRAY);
		var count = Component.literal(countStr).withStyle(ChatFormatting.WHITE);
		var after = Component.literal(t("riatoriifind.result.title.suffix.after").getString()).withStyle(ChatFormatting.GRAY);
		src.sendFeedback(Component.literal("").append(prefix).append(before).append(count).append(after));
		src.sendFeedback(divider());

		var colId = padRight(t("riatoriifind.result.col.id").getString(), maxId);
		var colGrade = padRight(t("riatoriifind.result.col.grade").getString(), maxGrade);
		src.sendFeedback(
			gray(Component.literal(colId))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(gray(Component.literal(colGrade)))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(gray(Component.literal(t("riatoriifind.result.col.name").getString())))
		);
		src.sendFeedback(divider());

		for (var entry : results) {
			var line = Component.literal(padRight(entry.id(), maxId))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(padRight(entry.grade(), maxGrade)))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(entry.name()));
			var wikiUrl = "https://wiki.ria.red/wiki/" + entry.name();
			var link = Component.literal(
				t("riatoriifind.result.wiki_link").getString()
			).withStyle(Style.EMPTY
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(wikiUrl)))
				.withHoverEvent(new HoverEvent.ShowText(
					gray(t("riatoriifind.result.wiki_hover", wikiUrl))))
				.withColor(ChatFormatting.BLUE));
			src.sendFeedback(line.append(Component.literal(" ")).append(link));
		}
		src.sendFeedback(divider());
	}

	private static int displayWidth(String s) {
		int w = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			w += (c >= '一' && c <= '龥') ? 2 : 1;
		}
		return w;
	}

	private static String padRight(String s, int targetWidth) {
		int need = targetWidth - displayWidth(s);
		if (need <= 0) return s;
		return s + " ".repeat(need);
	}
}
