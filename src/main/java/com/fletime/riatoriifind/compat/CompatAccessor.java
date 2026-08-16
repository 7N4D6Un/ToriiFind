package com.fletime.riatoriifind.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.ToastManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

 // 26.1 与 26.2 反射兼容层
public final class CompatAccessor {

    private CompatAccessor() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static Method toastGetter;
    private static boolean toastOnGui;
    private static boolean toastResolved;

    private static Method chatGetter;
    private static Field hudField;
    private static boolean chatViaHud;
    private static boolean chatResolved;

    public static ToastManager toastManager(Minecraft mc) {
        if (!toastResolved) resolveToast();
        try {
            return (ToastManager) toastGetter.invoke(toastOnGui ? mc.gui : mc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ToastManager 反射调用失败", e);
        }
    }

    public static ChatComponent chat(Minecraft mc) {
        if (!chatResolved) resolveChat();
        try {
            Object target = mc.gui;
            if (chatViaHud) target = hudField.get(mc.gui);
            return (ChatComponent) chatGetter.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ChatComponent 反射调用失败", e);
        }
    }

    // 26.1.2: Minecraft.getToastManager()
    // 26.2+: Gui.toastManager()
    private static void resolveToast() {
        try {
            toastGetter = Minecraft.class.getMethod("getToastManager");
            toastOnGui = false;
        } catch (NoSuchMethodException e) {
            try {
                toastGetter = Gui.class.getMethod("toastManager");
                toastOnGui = true;
            } catch (NoSuchMethodException e2) {
                throw new IllegalStateException(
                        "当前 Minecraft 版本不受支持：找不到 Minecraft.getToastManager() 或 Gui.toastManager()", e2);
            }
        }
        toastResolved = true;
    }

    // 26.1.2: Gui.getChat()
    // 26.2+: Gui.hud.getChat()
    private static void resolveChat() {
        try {
            chatGetter = Gui.class.getMethod("getChat");
            chatViaHud = false;
        } catch (NoSuchMethodException e) {
            try {
                chatGetter = Class.forName("net.minecraft.client.gui.Hud").getMethod("getChat");
                hudField = Gui.class.getField("hud");
                chatViaHud = true;
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException(
                        "当前 Minecraft 版本不受支持：找不到 Gui.getChat() 或 Hud.getChat()", e2);
            }
        }
        chatResolved = true;
    }
}
