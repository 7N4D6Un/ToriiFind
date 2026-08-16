package com.fletime.riatoriifind.chat;

import com.fletime.riatoriifind.command.FindCommand;
import net.minecraft.network.chat.ClickEvent;

// 自定义点击聊天点击事件
public final class ModClickEvents {

	private ModClickEvents() {
		throw new UnsupportedOperationException("Utility class");
	}

	public record PageClick(int page) implements ClickEvent {
		@Override
		public Action action() {
			return Action.CUSTOM;
		}
	}

	public static boolean handle(ClickEvent event) {
		return switch (event) {
			case PageClick(int page) -> {
				FindCommand.showCachedPage(page);
				yield true;
			}
			default -> false;
		};
	}
}
