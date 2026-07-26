package com.fletime.riatoriifind.chat;

import com.fletime.riatoriifind.command.FindCommand;
import com.fletime.riatoriifind.service.Navigator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

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

	public record NavClick(int x, int z) implements ClickEvent {
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
			case NavClick(int x, int z) -> {
				Navigator.setTarget(x, 64, z);
				var mc = Minecraft.getInstance();
				mc.gui.hud.getChat().addClientSystemMessage(
						Component.translatable("riatoriifind.command.go", x, z));
				mc.gui.setScreen(null);
				yield true;
			}
			default -> false;
		};
	}
}
