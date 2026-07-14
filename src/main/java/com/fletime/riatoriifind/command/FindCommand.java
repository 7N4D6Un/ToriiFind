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

	private static final int PAGE_SIZE = 10;

	public static int searchFind(CommandContext<FabricClientCommandSource> ctx, String query, int page) {
		if (ModConfig.debugMode) RiaToriiFind.LOGGER.info("搜索请求: \"{}\" 第{}页", query, page);

		try {
			var all = ToriiDataService.searchAll(query);
			displayFindResults(ctx, all, query, page);
		} catch (Exception e) {
			ctx.getSource().sendError(red(t("riatoriifind.error.load_data", e.getMessage())));
		}
		return 1;
	}

	private static void displayFindResults(CommandContext<FabricClientCommandSource> ctx, List<FindEntry> all, String query, int page) {
		var src = ctx.getSource();
		if (all.isEmpty()) {
			src.sendFeedback(divider());
			src.sendFeedback(red(t("riatoriifind.result.empty.find")));
			src.sendFeedback(divider());
			return;
		}

		int totalPages = (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		if (page < 1) page = 1;
		if (page > totalPages) page = totalPages;

		int from = (page - 1) * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, all.size());
		var pageEntries = all.subList(from, to);

		int maxId = 2, maxGrade = 2;
		for (var e : pageEntries) {
			maxId = Math.max(maxId, displayWidth(e.id()));
			maxGrade = Math.max(maxGrade, displayWidth(e.grade()));
		}

		src.sendFeedback(divider());
		var countStr = String.valueOf(all.size());
		src.sendFeedback(Component.literal("").append(
				Component.literal(t("riatoriifind.result.title.prefix").getString()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append(Component.literal(t("riatoriifind.result.title.suffix.before").getString()).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(countStr).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(t("riatoriifind.result.title.suffix.after").getString()).withStyle(ChatFormatting.GRAY)));
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

		for (var entry : pageEntries) {
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

		var footer = Component.literal(t("riatoriifind.result.page", page, totalPages).getString()).withStyle(ChatFormatting.GRAY);
		if (page < totalPages) {
			footer.append(Component.literal("  "));
			String nextCmd = "/riatoriifind " + query + " " + (page + 1);
			footer.append(Component.literal(t("riatoriifind.result.next_page").getString())
					.withStyle(Style.EMPTY
							.withClickEvent(new ClickEvent.SuggestCommand(nextCmd))
							.withHoverEvent(new HoverEvent.ShowText(gray(t("riatoriifind.result.next_page_hover"))))
							.withColor(ChatFormatting.GREEN)));
		}
		src.sendFeedback(footer);
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
