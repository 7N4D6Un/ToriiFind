package com.fletime.riatoriifind.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class CommandUtil {

	private CommandUtil() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static Component t(String key, Object... args) {
		return Component.translatable(key, args);
	}

	public static MutableComponent gray(Component c) {
		return c.copy().withStyle(ChatFormatting.GRAY);
	}

	public static MutableComponent gold(Component c) {
		return c.copy().withStyle(ChatFormatting.GOLD);
	}

	public static MutableComponent red(Component c) {
		return c.copy().withStyle(ChatFormatting.RED);
	}

	public static Component divider() {
		return t("riatoriifind.divider").copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
	}
}
