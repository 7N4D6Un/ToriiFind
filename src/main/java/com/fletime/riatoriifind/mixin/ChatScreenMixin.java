package com.fletime.riatoriifind.mixin;

import com.fletime.riatoriifind.chat.ModClickEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 拦截原版聊天组件点击
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void riatoriifind$handleModClickEvents(Style clicked, boolean allowInsertions,
			CallbackInfoReturnable<Boolean> cir) {
		if (allowInsertions || clicked == null) return;
		var event = clicked.getClickEvent();
		if (event != null && ModClickEvents.handle(event)) {
			cir.setReturnValue(true);
		}
	}
}
