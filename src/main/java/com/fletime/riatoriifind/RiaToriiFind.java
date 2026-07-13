package com.fletime.riatoriifind;

import com.fletime.riatoriifind.command.ToriiFindCommand;
import com.fletime.riatoriifind.config.ModConfig;
import com.fletime.riatoriifind.service.SourceCheckService;
import com.fletime.riatoriifind.service.ToriiDataService;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class RiaToriiFind implements ClientModInitializer {
    public static final String MOD_ID = "riatoriifind";
    public static final Logger LOGGER = LoggerFactory.getLogger("RiaToriiFind");

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        if (ModConfig.debugMode) LOGGER.info("配置已加载，当前源: {}", ModConfig.currentSource);
        ToriiDataService.preloadAsync();
        ToriiFindCommand.register();
        checkOnlineSource();
    }

    // 仅在当前源为远程时启动异步数据更新
    private static void checkOnlineSource() {
        if (!ModConfig.SOURCE_RIA_WIKI.equals(ModConfig.currentSource)) return;

        if (ModConfig.debugMode) LOGGER.info("开始检查远程数据源更新");
        SourceCheckService.check(ModConfig.RIA_WIKI_URL, 5, 3000L)
                .thenCompose(connected -> {
                    if (!connected) {
                        ModConfig.currentSource = ModConfig.SOURCE_LOCAL;
                        ModConfig.syncBuiltinData();
                        ToriiDataService.reloadCache();
                        if (ModConfig.debugMode) LOGGER.warn("远程源连接失败，已回退到本地源");
                        return CompletableFuture.completedFuture(false);
                    }
                    return SourceCheckService.updateFromRemote(ModConfig.RIA_WIKI_URL, ModConfig.getLocalDataFile())
                            .thenApply(updated -> {
                                if (updated) {
                                    ToriiDataService.reloadCache();
                                    if (ModConfig.debugMode) LOGGER.info("远程数据已更新");
                                }
                                return updated;
                            });
                });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
