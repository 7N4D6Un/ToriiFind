package com.fletime.toriifind;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import com.fletime.toriifind.config.SourceConfig;
import com.fletime.toriifind.service.LynnApiService;
import com.fletime.toriifind.service.LynnJsonService;
import com.fletime.toriifind.service.SourceStatusService;
import com.fletime.toriifind.service.AsyncSourceStatusService;
import com.fletime.toriifind.service.MirrorStatusService;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * ToriiFindCommand 命令注册与处理类。
 * 负责注册 /toriifind 相关命令，并实现数据搜索、拼音支持、结果展示等功能。
 */
public class ToriiFindCommand {
    // 零洲数据类
    private static class Torii {
        private final String id;
        private final String name;
        private final String level;

        /**
         * 构造函数
         * @param id 编号
         * @param name 名称
         * @param level 等级
         */
        public Torii(String id, String name, String level) {
            this.id = id;
            this.name = name;
            this.level = level;
        }

        @Override
        public String toString() {
            return id + " " + level + " " + name;
        }
    }

    // 后土数据类
    private static class Houtu {
        private final String id;
        private final String name;
        private final String level;

        /**
         * 构造函数
         * @param id 编号
         * @param name 名称
         * @param level 等级
         */
        public Houtu(String id, String name, String level) {
            this.id = id;
            this.name = name;
            this.level = level;
        }

        @Override
        public String toString() {
            return id + " " + level + " " + name;
        }
    }

    // 拼音格式化工具（单例）
    private static HanyuPinyinOutputFormat pinyinFormat;
    private static HanyuPinyinOutputFormat getPinyinFormat() {
        if (pinyinFormat == null) {
            pinyinFormat = new HanyuPinyinOutputFormat();
            // 小写，不带声调
            pinyinFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
            pinyinFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        }
        return pinyinFormat;
    }
    
    /**
     * 将中文字符串转换为拼音字符串（不带声调），抄来的，爽
     * @param chineseStr 中文字符串
     * @return 对应的拼音字符串，非中文字符保持不变
     */
    private static String toPinyin(String chineseStr) {
        if (chineseStr == null || chineseStr.isEmpty()) {
            return "";
        }
        StringBuilder pinyinBuilder = new StringBuilder();
        char[] chars = chineseStr.toCharArray();
        try {
            for (char c : chars) {
                // 判断是否是汉字
                if (Character.toString(c).matches("[\\u4E00-\\u9FA5]+")) {
                    // 将汉字转为拼音数组（多音字会返回多个拼音）
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, getPinyinFormat());
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        // 只取第一个拼音（对于多音字）
                        pinyinBuilder.append(pinyinArray[0]);
                    }
                } else {
                    // 非汉字直接添加
                    pinyinBuilder.append(c);
                }
            }
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            // 转换失败时直回原字符串
            return chineseStr;
        }
        return pinyinBuilder.toString();
    }

    /**
     * 注册所有 toriifind 相关命令
     * @param dispatcher 命令分发器
     */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerCommands(dispatcher);
        });
    }

    /**
     * 注册命令结构
     * /toriifind help
     * /toriifind zeroth <number or keyword>
     * /toriifind houtu <number or keyword>
     * /toriifind source list
     * /toriifind source switch <name>
     * /toriifind source current
     * /toriifind source status
     * /toriifind ciallo
     */
    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("toriifind")
                .executes(context -> showHelp(context))
                .then(literal("help")
                    .executes(context -> showHelp(context)))
                .then(literal("zeroth")
                    .then(argument("query", StringArgumentType.greedyString())
                        .executes(context -> searchZerothSmart(context, StringArgumentType.getString(context, "query")))))
                .then(literal("houtu")
                    .then(argument("query", StringArgumentType.greedyString())
                        .executes(context -> searchHoutuSmart(context, StringArgumentType.getString(context, "query")))))
                .then(literal("source")
                    .then(literal("list")
                        .executes(context -> listSources(context)))
                    .then(literal("switch")
                        .then(argument("name", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                // 自动补全所有可用数据源
                                for (String name : ToriiFind.getAllSources().keySet()) {
                                    SourceConfig.DataSource ds = ToriiFind.getAllSources().get(name);
                                    if (ds.isEnabled()) {
                                        builder.suggest(name);
                                    }
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> switchSource(context, StringArgumentType.getString(context, "name")))))
                    .then(literal("current")
                        .executes(context -> showCurrentSource(context)))
                    .then(literal("check")
                        .executes(context -> checkAllSources(context)))
                    .then(literal("reload")
                        .executes(context -> reloadConfig(context))))
                .then(literal("ciallo")
                    .executes(context -> sendCialloMessage(context)))
        );
    }

    /**
     * 显示帮助信息
     */
    private static int showHelp(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.title"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.help"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.zeroth_smart"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.houtu_smart"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.source.list"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.source.switch"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.source.current"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.source.check"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.source.reload"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.ciallo"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        return 1;
    }
    
    /**
     * 列出所有可用的数据源
     */
    private static int listSources(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.source.list.title"));
        
        Map<String, SourceConfig.DataSource> sources = ToriiFind.getAllSources();
        String currentSource = ToriiFind.getCurrentSourceName();
        
        for (Map.Entry<String, SourceConfig.DataSource> entry : sources.entrySet()) {
            String name = entry.getKey();
            SourceConfig.DataSource source = entry.getValue();
            
            String prefix = name.equals(currentSource)
                ? ToriiFind.translate("toriifind.source.status.current").getString()
                : ToriiFind.translate("toriifind.source.status.other").getString();
            String status = source.isEnabled()
                ? ToriiFind.translate("toriifind.source.status.enabled").getString()
                : ToriiFind.translate("toriifind.source.status.disabled").getString();
            String mode = source.isApiMode()
                ? ToriiFind.translate("toriifind.source.status.api_mode").getString()
                : ToriiFind.translate("toriifind.source.status.json_mode").getString();

            StringBuilder info = new StringBuilder();
            info.append(prefix).append(name).append(" ").append(status).append(" ").append(mode);
            info.append(ToriiFind.translate("toriifind.source.info.separator").getString()).append(source.getName());

            // 显示版本信息（如果有）
            if (source.getVersion() != null) {
                info.append(ToriiFind.translate("toriifind.source.info.version", source.getVersion()).getString());
            }
            
            context.getSource().sendFeedback(Text.literal(info.toString()));
            
            // 显示镜像信息
            if (!source.isApiMode() && source.getMirrorUrls() != null && source.getMirrorUrls().length > 0) {
                context.getSource().sendFeedback(
                    ToriiFind.translate("toriifind.source.mirror.count", source.getMirrorUrls().length)
                );
            }
        }
        
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        return 1;
    }
    
    /**
     * 切换数据源
     */
    private static int switchSource(CommandContext<FabricClientCommandSource> context, String sourceName) {
        if (ToriiFind.switchSource(sourceName)) {
            context.getSource().sendFeedback(Text.translatable("toriifind.source.switch.success", sourceName));
        } else {
            context.getSource().sendError(Text.translatable("toriifind.source.switch.fail", sourceName));
        }
        return 1;
    }
    
    /**
     * 显示当前数据源
     */
    private static int showCurrentSource(CommandContext<FabricClientCommandSource> context) {
        String currentSource = ToriiFind.getCurrentSourceName();
        SourceConfig.DataSource source = ToriiFind.getAllSources().get(currentSource);
        
        if (source != null) {
            String mode = source.isApiMode()
                ? ToriiFind.translate("toriifind.source.status.api_mode").getString()
                : ToriiFind.translate("toriifind.source.status.json_mode").getString();
            context.getSource().sendFeedback(Text.translatable(
                "toriifind.source.current.simple", currentSource, mode
            ));

            if (source.isApiMode() && source.getApiBaseUrl() != null) {
                context.getSource().sendFeedback(Text.translatable(
                    "toriifind.source.current.api", source.getApiBaseUrl()
                ));
            } else if (!source.isApiMode() && source.getUrl() != null) {
                context.getSource().sendFeedback(Text.translatable(
                    "toriifind.source.current.json", source.getUrl()
                ));
            }
        } else {
            context.getSource().sendError(Text.translatable("toriifind.source.current.error"));
        }
        return 1;
    }
    
    /**
     * 检查所有数据源更新和状态
     */
    private static int checkAllSources(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.source.check.start"));

        Map<String, SourceConfig.DataSource> sources = ToriiFind.getAllSources();

        for (Map.Entry<String, SourceConfig.DataSource> entry : sources.entrySet()) {
            String sourceName = entry.getKey();
            SourceConfig.DataSource dataSource = entry.getValue();

            if (dataSource.isApiMode()) {
                // API模式：检查连接状态
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    return SourceStatusService.checkSourceStatus(dataSource);
                }).thenAcceptAsync(status -> {
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        context.getSource().sendFeedback(Text.translatable(
                            "toriifind.source.check.api",
                            sourceName,
                            status.getStatusText()
                        ));
                    });
                });
            } else {
                // JSON模式：检查更新并下载
                com.fletime.toriifind.service.LocalDataService.checkAndUpdateDataSource(sourceName, dataSource)
                    .thenAcceptAsync(updated -> {
                        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                            String updateStatus = updated
                                ? ToriiFind.translate("toriifind.source.check.updated").getString()
                                : ToriiFind.translate("toriifind.source.check.latest").getString();

                            StringBuilder info = new StringBuilder();
                            info.append(ToriiFind.translate("toriifind.source.check.json", sourceName, updateStatus).getString());

                            // 显示版本信息（如果存在）
                            String version = com.fletime.toriifind.service.LocalDataService.getLocalVersion(
                                com.fletime.toriifind.service.LocalDataService.getLocalDataFile(sourceName));
                            if (version != null && !version.isEmpty()) {
                                info.append(ToriiFind.translate("toriifind.source.check.version", version).getString());
                            }

                            context.getSource().sendFeedback(Text.literal(info.toString()));

                            // 如果有镜像，直接显示镜像状态
                            if (dataSource.getMirrorUrls() != null && dataSource.getMirrorUrls().length > 0) {
                                MirrorStatusService.checkAllMirrors(dataSource).thenAccept(mirrorStatuses -> {
                                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                        for (MirrorStatusService.MirrorStatus mirror : mirrorStatuses) {
                                            String prefix = mirror.isPrimary()
                                                ? ToriiFind.translate("toriifind.source.check.mirror.primary").getString()
                                                : ToriiFind.translate("toriifind.source.check.mirror.other").getString();
                                            String statusIcon = mirror.isAvailable()
                                                ? ToriiFind.translate("toriifind.source.check.mirror.available").getString()
                                                : ToriiFind.translate("toriifind.source.check.mirror.unavailable").getString();

                                            StringBuilder line = new StringBuilder();
                                            line.append("  ").append(prefix).append(" ");
                                            line.append(statusIcon).append(" ");
                                            line.append("§f").append(mirror.getUrlDisplayName()).append(" ");
                                            line.append(mirror.getStatusText());

                                            if (mirror.getVersion() != null) {
                                                line.append(" §7").append(mirror.getVersion());
                                            }

                                            context.getSource().sendFeedback(Text.literal(line.toString()));
                                        }
                                    });
                                });
                            }
                        });
                    }).exceptionally(throwable -> {
                        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                            context.getSource().sendError(Text.translatable(
                                "toriifind.source.check.failed",
                                sourceName,
                                throwable.getMessage()
                            ));
                        });
                        return null;
                    });
            }
        }
        
        return 1;
    }
    
    /**
     * 重新加载配置文件
     */
    private static int reloadConfig(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.config.reload.start"));
        
        boolean success = ToriiFind.reloadConfig();
        
        if (success) {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.config.reload.success"));
            
            // 显示当前数据源信息
            String currentSource = ToriiFind.getCurrentSourceName();
            SourceConfig.DataSource source = ToriiFind.getAllSources().get(currentSource);
            if (source != null) {
                String mode = source.isApiMode()
                    ? ToriiFind.translate("toriifind.source.status.api_mode").getString()
                    : ToriiFind.translate("toriifind.source.status.json_mode").getString();
                context.getSource().sendFeedback(Text.translatable(
                    "toriifind.source.current.simple", currentSource, mode
                ));
            }
        } else {
            context.getSource().sendError(ToriiFind.translate("toriifind.config.reload.fail"));
        }
        
        return 1;
    }

    /**
     * 按编号查找零洲鸟居
     */
    private static int searchZerothByNumber(CommandContext<FabricClientCommandSource> context, int number) {
        SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
        
        if (currentSource != null && currentSource.isApiMode()) {
            // Lynn API模式
            return searchLynnByNumber(context, String.valueOf(number), "zth");
        } else {
            // 传统JSON模式
            return searchZerothByNumberJson(context, number);
        }
    }
    
    /**
     * 传统JSON模式按编号查找零洲鸟居
     */
    private static int searchZerothByNumberJson(CommandContext<FabricClientCommandSource> context, int number) {
        List<Torii> results = new ArrayList<>();
        try {
            List<Torii> toriiList = loadZerothData();
            for (Torii torii : toriiList) {
                if (torii.id.equals(String.valueOf(number))) {
                    results.add(torii);
                }
            }
            displayZerothResults(context, results);
        } catch (Exception e) {
            context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
        }
        return 1;
    }
    
    /**
     * Lynn源按编号搜索
     */
    private static int searchLynnByNumber(CommandContext<FabricClientCommandSource> context, String number, String source) {
        try {
            SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
            if (currentSource.isApiMode()) {
                // API模式：异步查询
                context.getSource().sendFeedback(ToriiFind.translate("toriifind.query.working"));
                
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        LynnApiService.LynnLandmark landmark = LynnApiService.getLandmarkById(
                            currentSource.getApiBaseUrl(), source, number);
                        
                        List<LynnApiService.LynnLandmark> results = new ArrayList<>();
                        if (landmark != null) {
                            results.add(landmark);
                        }
                        return results;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).thenAcceptAsync(results -> {
                    // 在主线程显示结果
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        displayLynnResults(context, results);
                    });
                }).exceptionally(throwable -> {
                    // 在主线程显示错误
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        context.getSource().sendError(ToriiFind.translate("toriifind.error.config", throwable.getMessage()));
                    });
                    return null;
                });
            } else {
                // JSON模式：加载所有数据然后过滤
                List<LynnApiService.LynnLandmark> allLandmarks = LynnJsonService.loadFromDataSource(currentSource);
                List<LynnApiService.LynnLandmark> results = LynnJsonService.filterById(allLandmarks, number);
                displayLynnResults(context, results);
            }
        } catch (Exception e) {
            context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
        }
        return 1;
    }
    
    /**
     * 展示Lynn源搜索结果
     */
    private static void displayLynnResults(CommandContext<FabricClientCommandSource> context, List<LynnApiService.LynnLandmark> results) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        if (results.isEmpty()) {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.not_found"));
        } else {
            for (LynnApiService.LynnLandmark landmark : results) {
                // 基础信息
                String formattedText = String.format(
                    ToriiFind.translate("toriifind.result.format.entry").getString(),
                    landmark.getId(), landmark.getGrade(), landmark.getName()
                );
            
                // 添加坐标信息
                if (landmark.getCoordinates() != null && !landmark.getCoordinates().isUnknown()) {
                    formattedText += " §7" + landmark.getCoordinates();
                }
            
                // 添加状态信息
                if (!"Normal".equals(landmark.getStatus())) {
                    formattedText += " §c[" + landmark.getStatus() + "]";
                }
            
                MutableText baseText = Text.literal(formattedText + " ");
                String wikiUrl = "https://wiki.ria.red/wiki/" + landmark.getName();
                Style linkStyle = Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(wikiUrl)))
                    .withHoverEvent(new HoverEvent.ShowText(
                        ToriiFind.translate("toriifind.result.wiki_hover", wikiUrl)
                    ))
                    .withColor(Formatting.AQUA)
                    .withUnderline(true);
            
                MutableText linkText = Text.literal(
                    ToriiFind.translate("toriifind.result.wiki_link").getString()
                ).setStyle(linkStyle);
            
                context.getSource().sendFeedback(baseText.append(linkText));
            }            
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        }
    }

    /**
     * 按名称或拼音查找零洲鸟居
     */
    private static int searchZerothByNameOrPinyin(CommandContext<FabricClientCommandSource> context, String keyword) {
        SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
        
        if (currentSource != null && currentSource.isApiMode()) {
            // Lynn API模式
            return searchLynnByName(context, keyword, "zth");
        } else {
            // 传统JSON模式
            return searchZerothByNameOrPinyinJson(context, keyword);
        }
    }
    
    /**
     * 传统JSON模式按名称或拼音查找零洲鸟居
     */
    private static int searchZerothByNameOrPinyinJson(CommandContext<FabricClientCommandSource> context, String keyword) {
        List<Torii> results = new ArrayList<>();
        try {
            List<Torii> toriiList = loadZerothData();
            // 首先，按名称进行精确匹配
            for (Torii torii : toriiList) {
                if (torii.name.contains(keyword)) {
                    results.add(torii);
                }
            }
            // 如果没有找到，且关键字由字母组成，则尝试按拼音进行搜索
            if (results.isEmpty() && keyword.matches("^[a-zA-Z]+$")) {
                String lowercaseKeyword = keyword.toLowerCase();
                for (Torii torii : toriiList) {
                    String namePinyin = toPinyin(torii.name).toLowerCase();
                    if (namePinyin.contains(lowercaseKeyword)) {
                        results.add(torii);
                    }
                }
            }
            displayZerothResults(context, results);
        } catch (Exception e) {
            context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
        }
        return 1;
    }
    
    /**
     * Lynn源按名称搜索
     */
    private static int searchLynnByName(CommandContext<FabricClientCommandSource> context, String keyword, String source) {
        try {
            SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
            
            if (currentSource.isApiMode()) {
                // API模式：异步查询
                context.getSource().sendFeedback(ToriiFind.translate("toriifind.query.working"));
                
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return LynnApiService.searchLandmarks(currentSource.getApiBaseUrl(), source, keyword);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).thenAcceptAsync(results -> {
                    // 在主线程显示结果
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        displayLynnResults(context, results);
                    });
                }).exceptionally(throwable -> {
                    // 在主线程显示错误
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        context.getSource().sendError(ToriiFind.translate("toriifind.error.config", throwable.getMessage()));
                    });
                    return null;
                });
            } else {
                // JSON模式：加载所有数据然后过滤
                List<LynnApiService.LynnLandmark> allLandmarks = LynnJsonService.loadFromDataSource(currentSource);
                List<LynnApiService.LynnLandmark> results = LynnJsonService.filterByNameOrPinyin(allLandmarks, keyword, ToriiFindCommand::toPinyin);
                displayLynnResults(context, results);
            }
        } catch (Exception e) {
            context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
        }
        return 1;
    }

    /**
     * 按编号查找后土境地
     */
    private static int searchHoutuByNumber(CommandContext<FabricClientCommandSource> context, String number) {
        SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
        
        if (currentSource != null && currentSource.isApiMode()) {
            // Lynn API模式
            return searchLynnByNumber(context, number, "houtu");
        } else {
            // 传统JSON模式
            return searchHoutuByNumberJson(context, number);
        }
    }
    
    /**
     * 传统JSON模式按编号查找后土境地
     */
    private static int searchHoutuByNumberJson(CommandContext<FabricClientCommandSource> context, String number) {
        List<Houtu> results = new ArrayList<>();
        try {
            List<Houtu> houtuList = loadHoutuData();
            for (Houtu houtu : houtuList) {
                if (houtu.id.contains(number)) {
                    results.add(houtu);
                }
            }
            displayHoutuResults(context, results);
        } catch (Exception e) {
            context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
        }
        return 1;
    }

    /**
     * 按名称或拼音查找后土境地
     */
    private static int searchHoutuByNameOrPinyin(CommandContext<FabricClientCommandSource> context, String keyword) {
        SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
        
        if (currentSource != null && currentSource.isApiMode()) {
            // Lynn API模式
            return searchLynnByName(context, keyword, "houtu");
        } else {
            // 传统JSON模式
            return searchHoutuByNameOrPinyinJson(context, keyword);
        }
    }
    
    /**
     * 传统JSON模式按名称或拼音查找后土境地
     */
    private static int searchHoutuByNameOrPinyinJson(CommandContext<FabricClientCommandSource> context, String keyword) {
        List<Houtu> results = new ArrayList<>();
        try {
            List<Houtu> houtuList = loadHoutuData();
            for (Houtu houtu : houtuList) {
                if (houtu.name.contains(keyword)) {
                    results.add(houtu);
                }
            }
            if (results.isEmpty() && keyword.matches("^[a-zA-Z]+$")) {
                String lowercaseKeyword = keyword.toLowerCase();
                for (Houtu houtu : houtuList) {
                    String namePinyin = toPinyin(houtu.name).toLowerCase();
                    if (namePinyin.contains(lowercaseKeyword)) {
                        results.add(houtu);
                    }
                }
            }
            displayHoutuResults(context, results);
        } catch (Exception e) {
            context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
        }
        return 1;
    }

    /**
     * 智能查询零洲鸟居
     * 如果输入是纯数字，则按编号和名称都查找，合并去重后显示；否则按名称查找
     */
    private static int searchZerothSmart(CommandContext<FabricClientCommandSource> context, String query) {
        query = query.trim();
        // 判断输入是否为纯数字
        if (query.matches("^\\d+$")) {
            // 获取当前数据源
            SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
            final String finalQuery = query;
            final SourceConfig.DataSource finalSource = currentSource;
            if (currentSource != null && currentSource.isApiMode()) {
                // API模式：编号和名称分别异步查找，然后合并去重
                context.getSource().sendFeedback(ToriiFind.translate("toriifind.query.working"));
                // 按编号查找
                java.util.concurrent.CompletableFuture<List<LynnApiService.LynnLandmark>> futureId = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        LynnApiService.LynnLandmark landmark = LynnApiService.getLandmarkById(finalSource.getApiBaseUrl(), "zth", finalQuery);
                        List<LynnApiService.LynnLandmark> list = new ArrayList<>();
                        if (landmark != null) list.add(landmark);
                        return list;
                    } catch (Exception e) { return new ArrayList<>(); }
                });
                // 按名称查找
                java.util.concurrent.CompletableFuture<List<LynnApiService.LynnLandmark>> futureName = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return LynnApiService.searchLandmarks(finalSource.getApiBaseUrl(), "zth", finalQuery);
                    } catch (Exception e) { return new ArrayList<>(); }
                });
                // 合并编号和名称查找结果，去重后显示
                futureId.thenCombine(futureName, (list1, list2) -> {
                    java.util.LinkedHashMap<String, LynnApiService.LynnLandmark> map = new java.util.LinkedHashMap<>();
                    for (LynnApiService.LynnLandmark l : list1) map.put(l.getId(), l);
                    for (LynnApiService.LynnLandmark l : list2) map.put(l.getId(), l);
                    return new ArrayList<>(map.values());
                }).thenAcceptAsync(resultsList -> {
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        displayLynnResults(context, resultsList);
                    });
                });
                return 1;
            } else {
                // JSON模式：编号和名称都查找，合并去重
                try {
                    List<Torii> toriiList = loadZerothData();
                    List<Torii> results = new ArrayList<>();
                    // 按编号查找
                    for (Torii torii : toriiList) {
                        if (torii.id.equals(query)) results.add(torii);
                    }
                    // 按名称（包括拼音）模糊查找
                    for (Torii torii : toriiList) {
                        if (torii.name.contains(query) || toPinyin(torii.name).toLowerCase().contains(query.toLowerCase())) {
                            boolean exists = false;
                            for (Torii t : results) if (t.id.equals(torii.id)) { exists = true; break; }
                            if (!exists) results.add(torii);
                        }
                    }
                    // 显示合并后的结果
                    displayZerothResults(context, results);
                } catch (Exception e) {
                    context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
                }
                return 1;
            }
        } else {
            // 非纯数字，直接按名称查找（支持拼音）
            return searchZerothByNameOrPinyin(context, query);
        }
    }

    /**
     * 智能查询后土境地
     * 如果输入是纯数字，则按编号和名称都查找，合并去重后显示；否则按名称查找
     */
    private static int searchHoutuSmart(CommandContext<FabricClientCommandSource> context, String query) {
        query = query.trim();
        // 判断输入是否为纯数字
        if (query.matches("^\\d+$")) {
            // 获取当前数据源
            SourceConfig.DataSource currentSource = ToriiFind.getSourceConfig().getCurrentDataSource();
            final String finalQuery = query;
            final SourceConfig.DataSource finalSource = currentSource;
            if (currentSource != null && currentSource.isApiMode()) {
                // API模式：编号和名称分别异步查找，然后合并去重
                context.getSource().sendFeedback(ToriiFind.translate("toriifind.query.working"));
                // 按编号查找
                java.util.concurrent.CompletableFuture<List<LynnApiService.LynnLandmark>> futureId = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        LynnApiService.LynnLandmark landmark = LynnApiService.getLandmarkById(finalSource.getApiBaseUrl(), "houtu", finalQuery);
                        List<LynnApiService.LynnLandmark> list = new ArrayList<>();
                        if (landmark != null) list.add(landmark);
                        return list;
                    } catch (Exception e) { return new ArrayList<>(); }
                });
                // 按名称查找
                java.util.concurrent.CompletableFuture<List<LynnApiService.LynnLandmark>> futureName = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return LynnApiService.searchLandmarks(finalSource.getApiBaseUrl(), "houtu", finalQuery);
                    } catch (Exception e) { return new ArrayList<>(); }
                });
                // 合并编号和名称查找结果，去重后显示
                futureId.thenCombine(futureName, (list1, list2) -> {
                    java.util.LinkedHashMap<String, LynnApiService.LynnLandmark> map = new java.util.LinkedHashMap<>();
                    for (LynnApiService.LynnLandmark l : list1) map.put(l.getId(), l);
                    for (LynnApiService.LynnLandmark l : list2) map.put(l.getId(), l);
                    return new ArrayList<>(map.values());
                }).thenAcceptAsync(resultsList -> {
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                        displayLynnResults(context, resultsList);
                    });
                });
                return 1;
            } else {
                // JSON模式：编号和名称都查找，合并去重
                try {
                    List<Houtu> houtuList = loadHoutuData();
                    List<Houtu> results = new ArrayList<>();
                    // 按编号查找
                    for (Houtu houtu : houtuList) {
                        if (houtu.id.contains(query)) results.add(houtu);
                    }
                    // 按名称（包括拼音）模糊查找
                    for (Houtu houtu : houtuList) {
                        if (houtu.name.contains(query) || toPinyin(houtu.name).toLowerCase().contains(query.toLowerCase())) {
                            boolean exists = false;
                            for (Houtu h : results) if (h.id.equals(houtu.id)) { exists = true; break; }
                            if (!exists) results.add(houtu);
                        }
                    }
                    // 显示合并后的结果
                    displayHoutuResults(context, results);
                } catch (Exception e) {
                    context.getSource().sendError(ToriiFind.translate("toriifind.error.config", e.getMessage()));
                }
                return 1;
            }
        } else {
            // 非纯数字，直接按名称查找（支持拼音）
            return searchHoutuByNameOrPinyin(context, query);
        }
    }

    /**
     * 展示零洲鸟居的搜索结果
     * @param context 命令上下文
     * @param results 结果列表
     */
    private static void displayZerothResults(CommandContext<FabricClientCommandSource> context, List<Torii> results) {
        if (results.isEmpty()) {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.empty.torii"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        } else {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.title", results.size()));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.header.torii"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));

            for (Torii torii : results) {
                String formattedText = String.format(
                    ToriiFind.translate("toriifind.result.format.entry").getString(),
                    torii.id, torii.level, torii.name
                );
                MutableText baseText = Text.literal(formattedText + " ");
                String wikiUrl = "https://wiki.ria.red/wiki/" + torii.name;

                Style linkStyle = Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(wikiUrl)))
                    .withHoverEvent(new HoverEvent.ShowText(
                        ToriiFind.translate("toriifind.result.wiki_hover", wikiUrl)
                    ))
                    .withColor(Formatting.AQUA)
                    .withUnderline(true);
            
                MutableText linkText = Text.literal(
                    ToriiFind.translate("toriifind.result.wiki_link").getString()
                ).setStyle(linkStyle);
                context.getSource().sendFeedback(baseText.append(linkText));
            }
            
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        }
    }

    /**
     * 展示后土数据的搜索结果
     * @param context 命令上下文
     * @param results 结果列表
     */
    private static void displayHoutuResults(CommandContext<FabricClientCommandSource> context, List<Houtu> results) {
        if (results.isEmpty()) {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.empty.houtu"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        } else {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.title", results.size()));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.header.houtu"));
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));

            for (Houtu houtu : results) {
                String formattedText = String.format(
                    ToriiFind.translate("toriifind.result.format.entry").getString(),
                    houtu.id, houtu.level, houtu.name
                );
                MutableText baseText = Text.literal(formattedText + " ");
                String wikiUrl = "https://wiki.ria.red/wiki/" + houtu.name;
            
                Style linkStyle = Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(wikiUrl)))
                    .withHoverEvent(new HoverEvent.ShowText(
                        ToriiFind.translate("toriifind.result.wiki_hover", wikiUrl)
                    ))
                    .withColor(Formatting.AQUA)
                    .withUnderline(true);
            
                MutableText linkText = Text.literal(
                    ToriiFind.translate("toriifind.result.wiki_link").getString()
                ).setStyle(linkStyle);
            
                context.getSource().sendFeedback(baseText.append(linkText));
            }            
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        }
    }

    /**
     * 加载零洲数据（优先使用本地文件）
     * @return 零洲鸟居列表
     * @throws IOException 读取异常
     */
    private static List<Torii> loadZerothData() throws IOException {
        // 判断当前数据源是否为 local
        String currentSource = ToriiFind.getCurrentSourceName();
        if ("local".equals(currentSource)) {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            Path configFile = configDir.resolve("toriifind.json");
            return loadZerothDataFromFile(configFile);
        }
        // 其它数据源逻辑不变
        Path localFile = com.fletime.toriifind.service.LocalDataService.getLocalDataFile(currentSource);
        if (java.nio.file.Files.exists(localFile)) {
            try {
                return loadZerothDataFromFile(localFile);
            } catch (Exception e) {
                System.err.println("[ToriiFind] 读取本地零洲数据失败，尝试从传统配置文件读取: " + e.getMessage());
            }
        }
        Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("toriifind.json");
        return loadZerothDataFromFile(configFile);
    }
    
    /**
     * 从指定文件加载零洲数据
     */
    private static List<Torii> loadZerothDataFromFile(Path file) throws IOException {
        List<Torii> toriiList = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray zerothArray = jsonObject.getAsJsonArray("zeroth");
            for (int i = 0; i < zerothArray.size(); i++) {
                JsonObject toriiObject = zerothArray.get(i).getAsJsonObject();
                String id = toriiObject.get("id").getAsString();
                String name = toriiObject.get("name").getAsString();
                String level = toriiObject.get("grade").getAsString();
                Torii torii = new Torii(id, name, level);
                toriiList.add(torii);
            }
        }
        return toriiList;
    }

    /**
     * 加载后土数据（优先使用本地文件）
     * @return 后土境地列表
     * @throws IOException 读取异常
     */
    private static List<Houtu> loadHoutuData() throws IOException {
        // 判断当前数据源是否为 local
        String currentSource = ToriiFind.getCurrentSourceName();
        if ("local".equals(currentSource)) {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            Path configFile = configDir.resolve("toriifind.json");
            return loadHoutuDataFromFile(configFile);
        }
        // 其它数据源逻辑不变
        Path localFile = com.fletime.toriifind.service.LocalDataService.getLocalDataFile(currentSource);
        if (java.nio.file.Files.exists(localFile)) {
            try {
                return loadHoutuDataFromFile(localFile);
            } catch (Exception e) {
                System.err.println("[ToriiFind] 读取本地后土数据失败，尝试从传统配置文件读取: " + e.getMessage());
            }
        }
        Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("toriifind.json");
        return loadHoutuDataFromFile(configFile);
    }
    
    /**
     * 从指定文件加载后土数据
     */
    private static List<Houtu> loadHoutuDataFromFile(Path file) throws IOException {
        List<Houtu> houtuList = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray houtuArray = jsonObject.getAsJsonArray("houtu");
            for (int i = 0; i < houtuArray.size(); i++) {
                JsonObject houtuObject = houtuArray.get(i).getAsJsonObject();
                String id = houtuObject.get("id").getAsString();
                String name = houtuObject.get("name").getAsString();
                String level = houtuObject.get("grade").getAsString();
                Houtu houtu = new Houtu(id, name, level);
                houtuList.add(houtu);
            }
        }
        return houtuList;
    }

    /**
     * 向公屏发送 𝑪𝒊𝒂𝒍𝒍𝒐～(∠・ω< )⌒★
     * @param context 命令上下文
     * @return 执行结果
     */
    private static int sendCialloMessage(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatMessage("Ciallo～(∠・ω< )⌒☆");
        }
        return 1;
    }
} 