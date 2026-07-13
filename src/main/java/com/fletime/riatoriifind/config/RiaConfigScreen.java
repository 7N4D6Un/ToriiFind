package com.fletime.riatoriifind.config;

import com.fletime.riatoriifind.service.SourceCheckService;
import com.fletime.riatoriifind.service.ToriiDataService;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class RiaConfigScreen {

    private RiaConfigScreen() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Screen create(Screen parent) {
        var prevSource = ModConfig.currentSource;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("riatoriifind.config.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("riatoriifind.config.category.general"));

        general.addEntry(entryBuilder
                .startSelector(
                        Component.translatable("riatoriifind.config.source"),
                        ModConfig.SOURCES,
                        ModConfig.currentSource)
                .setDefaultValue(ModConfig.SOURCE_LOCAL)
                .setTooltip(Component.translatable("riatoriifind.config.source.tooltip"))
                .setNameProvider(str -> Component.translatable("riatoriifind.config.source." + str))
                .setSaveConsumer(str -> ModConfig.currentSource = str)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("riatoriifind.config.show_check_popups"),
                        ModConfig.showCheckPopups)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("riatoriifind.config.show_check_popups.tooltip"))
                .setSaveConsumer(v -> ModConfig.showCheckPopups = v)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("riatoriifind.config.refresh_source"),
                        ModConfig.refreshSource)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("riatoriifind.config.refresh_source.tooltip"))
                .setSaveConsumer(v -> ModConfig.refreshSource = v)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("riatoriifind.config.debug_mode"),
                        ModConfig.debugMode)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("riatoriifind.config.debug_mode.tooltip"))
                .setSaveConsumer(v -> ModConfig.debugMode = v)
                .build());

        builder.setSavingRunnable(() -> {
            var needsRefresh = ModConfig.refreshSource;
            if (needsRefresh) {
                ModConfig.refreshSource = false;
            }

            ModConfig.save();
            if (needsRefresh) {
                refreshCurrentSource();
            } else if (!prevSource.equals(ModConfig.currentSource)) {
                performSourceSwitch();
            }
        });

        return builder.build();
    }

    private static void refreshCurrentSource() {
        if (ModConfig.SOURCE_RIA_WIKI.equals(ModConfig.currentSource)) {
            ToriiDataService.reloadCache();
            SourceCheckService.check(ModConfig.RIA_WIKI_URL, 5, 3000L)
                    .thenCompose(connected -> {
                        if (!connected) {
                            ModConfig.currentSource = ModConfig.SOURCE_LOCAL;
                            ModConfig.syncBuiltinData();
                            ToriiDataService.reloadCache();
                            return CompletableFuture.completedFuture(false);
                        }
                        return SourceCheckService.updateFromRemote(ModConfig.RIA_WIKI_URL, ModConfig.getLocalDataFile())
                                .thenApply(updated -> {
                                    if (updated) ToriiDataService.reloadCache();
                                    return true;
                                });
                    })
                    .thenAccept(success -> {
                        if (!ModConfig.showCheckPopups) return;
                        Minecraft.getInstance().execute(() ->
                                showToast(success
                                        ? Component.translatable("riatoriifind.source.check.success.title")
                                        : Component.translatable("riatoriifind.source.check.fallback.title"),
                                        success
                                                ? Component.translatable("riatoriifind.source.check.success.message")
                                                : Component.translatable("riatoriifind.source.check.fallback.message")));
                    });
        } else {
            ModConfig.syncBuiltinData();
            ToriiDataService.reloadCache();
        }
    }

    private static void performSourceSwitch() {
        if (ModConfig.SOURCE_RIA_WIKI.equals(ModConfig.currentSource)) {
            ToriiDataService.reloadCache();
            SourceCheckService.check(ModConfig.RIA_WIKI_URL, 5, 3000L)
                    .thenCompose(connected -> {
                        if (!connected) {
                            ModConfig.currentSource = ModConfig.SOURCE_LOCAL;
                            ModConfig.syncBuiltinData();
                            ToriiDataService.reloadCache();
                            return CompletableFuture.completedFuture(false);
                        }
                        return SourceCheckService.updateFromRemote(ModConfig.RIA_WIKI_URL, ModConfig.getLocalDataFile())
                                .thenApply(updated -> {
                                    if (updated) ToriiDataService.reloadCache();
                                    return true;
                                });
                    })
                    .thenAccept(success -> {
                        if (!ModConfig.showCheckPopups) return;
                        Minecraft.getInstance().execute(() ->
                                showToast(success
                                        ? Component.translatable("riatoriifind.source.check.success.title")
                                        : Component.translatable("riatoriifind.source.check.fallback.title"),
                                        success
                                                ? Component.translatable("riatoriifind.source.check.success.message")
                                                : Component.translatable("riatoriifind.source.check.fallback.message")));
                    });
        } else {
            ModConfig.syncBuiltinData();
            ToriiDataService.reloadCache();
        }
    }

    private static void showToast(Component title, Component message) {
        SystemToast.add(
                Minecraft.getInstance().gui.toastManager(),
                new SystemToast.SystemToastId(),
                title, message);
    }
}
