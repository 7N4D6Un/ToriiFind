package com.fletime.riatoriifind.command;

import com.fletime.riatoriifind.RiaToriiFind;
import com.fletime.riatoriifind.chat.ModClickEvents;
import com.fletime.riatoriifind.compat.CompatAccessor;
import com.fletime.riatoriifind.config.ModConfig;
import com.fletime.riatoriifind.service.ToriiDataService;
import com.fletime.riatoriifind.service.ToriiDataService.FindEntry;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import static com.fletime.riatoriifind.command.CommandUtil.*;

public class FindCommand {

	private static final int PAGE_SIZE = 10;

	// 最近一次搜索的结果快照，翻页点击直接渲染缓存，无需重新搜索
	private record PageSession(String query, List<FindEntry> results) {}
	private static PageSession session;

	// 数据源刷新/切换后由 ToriiDataService 调用，避免翻页渲染过期快照
	public static void clearSession() {
		session = null;
	}

	public static int searchFind(CommandContext<FabricClientCommandSource> ctx, String query) {
		if (ModConfig.debugMode) RiaToriiFind.LOGGER.info("搜索请求: \"{}\"", query);

		try {
			var all = ToriiDataService.searchAll(query);
			session = new PageSession(query, all);
			displayFindResults(ctx.getSource()::sendFeedback, all, 1);
		} catch (Exception e) {
			ctx.getSource().sendError(red(t("riatoriifind.error.load_data", e.getMessage())));
		}
		return 1;
	}

	// 翻页入口：由 [上一页]/[下一页] 点击事件触发（经 ChatScreenMixin），无指令上下文
	public static void showCachedPage(int page) {
		Consumer<Component> chat = msg ->
				CompatAccessor.chat(Minecraft.getInstance()).addClientSystemMessage(msg);
		if (session == null) {
			chat.accept(red(t("riatoriifind.error.no_session")));
			return;
		}
		displayFindResults(chat, session.results(), page);
	}

	private static void displayFindResults(Consumer<Component> out, List<FindEntry> all, int page) {
		if (all.isEmpty()) {
			out.accept(divider());
			out.accept(red(t("riatoriifind.result.empty.find")));
			out.accept(divider());
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

		out.accept(divider());
		var countStr = String.valueOf(all.size());
		out.accept(Component.literal("")
				.append(t("riatoriifind.result.title.prefix").copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append(t("riatoriifind.result.title.suffix.before").copy().withStyle(ChatFormatting.GRAY))
				.append(Component.literal(countStr).withStyle(ChatFormatting.WHITE))
				.append(t("riatoriifind.result.title.suffix.after").copy().withStyle(ChatFormatting.GRAY)));
		out.accept(divider());

		var colId = padRight(t("riatoriifind.result.col.id").getString(), maxId);
		var colGrade = padRight(t("riatoriifind.result.col.grade").getString(), maxGrade);
		out.accept(
			gray(Component.literal(colId))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(gray(Component.literal(colGrade)))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(gray(t("riatoriifind.result.col.name")))
		);
		out.accept(divider());

		for (var entry : pageEntries) {
			var line = Component.literal(padRight(entry.id(), maxId))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(padRight(entry.grade(), maxGrade)))
				.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(entry.name()));

			var wikiUrl = "https://wiki.ria.red/wiki/" + entry.name();
			var link = t("riatoriifind.result.wiki_link").copy().withStyle(Style.EMPTY
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(wikiUrl)))
				.withHoverEvent(new HoverEvent.ShowText(
					gray(t("riatoriifind.result.wiki_hover", wikiUrl))))
				.withColor(ChatFormatting.BLUE));
			line.append(Component.literal(" ")).append(link);

			out.accept(line);
		}
		out.accept(divider());

		// 页脚：首页不显示 [上一页]，尾页不显示 [下一页]，点击自动翻页
		var footer = t("riatoriifind.result.page", page, totalPages).copy().withStyle(ChatFormatting.GRAY);
		if (page > 1) {
			footer.append(Component.literal("  "));
			footer.append(pageLink(t("riatoriifind.result.prev_page"), page - 1,
					t("riatoriifind.result.prev_page_hover")));
		}
		if (page < totalPages) {
			footer.append(Component.literal("  "));
			footer.append(pageLink(t("riatoriifind.result.next_page"), page + 1,
					t("riatoriifind.result.next_page_hover")));
		}
		out.accept(footer);
		out.accept(divider());
	}

	private static Component pageLink(Component label, int targetPage, Component hover) {
		return label.copy().withStyle(Style.EMPTY
				.withClickEvent(new ModClickEvents.PageClick(targetPage))
				.withHoverEvent(new HoverEvent.ShowText(gray(hover)))
				.withColor(ChatFormatting.GREEN));
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
