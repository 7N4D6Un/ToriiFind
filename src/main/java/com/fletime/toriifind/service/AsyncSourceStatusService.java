package com.fletime.toriifind.service;

import com.fletime.toriifind.config.SourceConfig;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

public class AsyncSourceStatusService {
    
    private static final Executor ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "ToriiFind-StatusCheck");
        thread.setDaemon(true);
        return thread;
    });
    
    private static final int MAX_CONCURRENT_CHECKS = 3;
    private static final int TIMEOUT_SECONDS = 5;
    
    /**
     * 异步检查所有数据源状态
     * @param context 命令上下文
     * @param sources 数据源配置
     */
    public static void checkAllSourcesAsync(FabricClientCommandSource context, Map<String, SourceConfig.DataSource> sources) {
        // 显示开始提示
        context.sendFeedback(Text.literal("§6正在检测数据源状态... §7(检测可能需要几秒钟)"));
        context.sendFeedback(Text.literal(""));
        
        // 状态结果存储
        Map<String, SourceStatusService.SourceStatus> results = new ConcurrentHashMap<>();
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalSources = sources.size();
        
        // 为每个数据源创建异步检测任务
        for (Map.Entry<String, SourceConfig.DataSource> entry : sources.entrySet()) {
            String sourceName = entry.getKey();
            SourceConfig.DataSource dataSource = entry.getValue();
            
            CompletableFuture<Void> checkTask = CompletableFuture.supplyAsync(() -> {
                try {
                    if (dataSource.isEnabled()) {
                        return SourceStatusService.checkSourceStatus(dataSource);
                    } else {
                        return new SourceStatusService.SourceStatus(false, null, 0, "已禁用");
                    }
                } catch (Exception e) {
                    return new SourceStatusService.SourceStatus(false, null, 0, "检测异常: " + e.getMessage());
                }
            }, ASYNC_EXECUTOR)
            .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .handle((status, throwable) -> {
                if (throwable != null) {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        status = new SourceStatusService.SourceStatus(false, null, 0, "检测超时");
                    } else {
                        status = new SourceStatusService.SourceStatus(false, null, 0, "网络错误");
                    }
                }
                
                results.put(sourceName, status);
                int completed = completedCount.incrementAndGet();
                
                // 显示进度
                sendProgressUpdate(context, sourceName, status, completed, totalSources);
                
                return null;
            });
        }
        
        // 等待所有检测完成或超时，然后显示汇总结果
        CompletableFuture.allOf(
            results.entrySet().stream()
                .map(entry -> CompletableFuture.completedFuture(entry))
                .toArray(CompletableFuture[]::new)
        ).thenRunAsync(() -> {
            // 等待一小段时间确保所有进度更新都完成
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 在主线程显示最终结果
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                showFinalResults(context, sources, results);
            });
        }, ASYNC_EXECUTOR);
    }
    
    /**
     * 发送进度更新（在主线程执行）
     */
    private static void sendProgressUpdate(FabricClientCommandSource context, String sourceName, 
                                         SourceStatusService.SourceStatus status, int completed, int total) {
        // 确保在主线程执行UI更新
        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
            String statusText = status.isAvailable() ? "§a✓" : "§c✗";
            String progressText = String.format("§7[%d/%d]", completed, total);
            
            context.sendFeedback(Text.literal(
                progressText + " §6" + sourceName + " " + statusText + " " + status.getStatusText()
            ));
        });
    }
    
    /**
     * 显示最终汇总结果
     */
    private static void showFinalResults(FabricClientCommandSource context, 
                                       Map<String, SourceConfig.DataSource> sources,
                                       Map<String, SourceStatusService.SourceStatus> results) {
        context.sendFeedback(Text.literal(""));
        context.sendFeedback(Text.literal("§6=== 检测完成 ==="));
        
        int availableCount = 0;
        int totalCount = sources.size();
        
        for (Map.Entry<String, SourceConfig.DataSource> entry : sources.entrySet()) {
            String name = entry.getKey();
            SourceConfig.DataSource source = entry.getValue();
            SourceStatusService.SourceStatus status = results.get(name);
            
            if (status != null && status.isAvailable()) {
                availableCount++;
            }
            
            String mode = source.isApiMode() ? "API模式" : "JSON模式";
            
            StringBuilder info = new StringBuilder();
            info.append("§6").append(name).append(" §f(").append(mode).append(") ");
            if (status != null) {
                info.append(status.getStatusText());
                
                // 显示版本信息
                if (status.getVersion() != null) {
                    info.append(" §7版本: ").append(status.getVersion());
                }
            } else {
                info.append("§c[检测失败]");
            }
            
            context.sendFeedback(Text.literal(info.toString()));
            
            // 显示镜像状态（Lynn JSON模式）
            if (!source.isApiMode() && source.getMirrorUrls() != null && source.getMirrorUrls().length > 0) {
                // 异步检查镜像状态
                MirrorStatusService.checkAllMirrors(source).thenAccept(mirrorStatuses -> {
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        showMirrorStatuses(context, name, mirrorStatuses);
                    });
                });
            }
        }
        
        // 显示统计信息
        context.sendFeedback(Text.literal(""));
        String summaryColor = availableCount == totalCount ? "§a" : availableCount > 0 ? "§e" : "§c";
        context.sendFeedback(Text.literal(String.format(
            "§6状态统计: %s%d/%d §6数据源可用", 
            summaryColor, availableCount, totalCount
        )));
        
        context.sendFeedback(Text.literal("§8" + "=".repeat(50)));
    }
    
    /**
     * 显示镜像状态
     */
    private static void showMirrorStatuses(FabricClientCommandSource context, String sourceName, List<MirrorStatusService.MirrorStatus> mirrors) {
        context.sendFeedback(Text.literal("  §7━━ §6" + sourceName + " §7镜像状态 ━━"));
        
        for (MirrorStatusService.MirrorStatus mirror : mirrors) {
            String prefix = mirror.isPrimary() ? "§b[主]" : "§7[镜像]";
            String statusIcon = mirror.isAvailable() ? "§a✓" : "§c✗";
            
            StringBuilder line = new StringBuilder();
            line.append("    ").append(prefix).append(" ");
            line.append(statusIcon).append(" ");
            line.append("§f").append(mirror.getUrlDisplayName()).append(" ");
            line.append(mirror.getStatusText());
            
            if (mirror.getVersion() != null) {
                line.append(" §7").append(mirror.getVersion());
            }
            
            context.sendFeedback(Text.literal(line.toString()));
        }
        
        // 显示最佳镜像推荐
        MirrorStatusService.MirrorStatus best = MirrorStatusService.getBestMirror(mirrors);
        if (best != null) {
            context.sendFeedback(Text.literal("    §a推荐: §f" + best.getUrlDisplayName() + " §7(最快)"));
        }
    }
}