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
     * /toriifind zeroth num <number>
     * /toriifind zeroth name <keyword>
     * /toriifind houtu num <number>
     * /toriifind houtu name <keyword>
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
                    .then(literal("num")
                        .then(argument("number", IntegerArgumentType.integer(1))
                            .executes(context -> searchZerothByNumber(context, IntegerArgumentType.getInteger(context, "number")))))
                    .then(literal("name")
                        .then(argument("keyword", StringArgumentType.greedyString())
                            .executes(context -> searchZerothByNameOrPinyin(context, StringArgumentType.getString(context, "keyword"))))))
                .then(literal("houtu")
                    .then(literal("num")
                        .then(argument("number", StringArgumentType.string())
                            .executes(context -> searchHoutuByNumber(context, StringArgumentType.getString(context, "number")))))
                    .then(literal("name")
                        .then(argument("keyword", StringArgumentType.greedyString())
                            .executes(context -> searchHoutuByNameOrPinyin(context, StringArgumentType.getString(context, "keyword"))))))
                .then(literal("source")
                    .then(literal("list")
                        .executes(context -> listSources(context)))
                    .then(literal("switch")
                        .then(argument("name", StringArgumentType.string())
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
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.zeroth_num"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.zeroth_name"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.houtu_num"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.houtu_name"));
        context.getSource().sendFeedback(Text.literal("§6/toriifind source list §f- 列出所有可用的数据源"));
        context.getSource().sendFeedback(Text.literal("§6/toriifind source switch <name> §f- 切换到指定数据源"));
        context.getSource().sendFeedback(Text.literal("§6/toriifind source current §f- 显示当前使用的数据源"));
        context.getSource().sendFeedback(Text.literal("§6/toriifind source check §f- 检查所有数据源更新和状态"));
        context.getSource().sendFeedback(Text.literal("§6/toriifind source reload §f- 重新加载配置文件"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.ciallo"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        return 1;
    }
    
    /**
     * 列出所有可用的数据源
     */
    private static int listSources(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        context.getSource().sendFeedback(Text.literal("§6可用的数据源："));
        
        Map<String, SourceConfig.DataSource> sources = ToriiFind.getAllSources();
        String currentSource = ToriiFind.getCurrentSourceName();
        
        for (Map.Entry<String, SourceConfig.DataSource> entry : sources.entrySet()) {
            String name = entry.getKey();
            SourceConfig.DataSource source = entry.getValue();
            
            String prefix = name.equals(currentSource) ? "§a[当前] " : "§7";
            String status = source.isEnabled() ? "§a[启用]" : "§c[禁用]";
            String mode = source.isApiMode() ? "§b[API模式]" : "§e[JSON模式]";
            
            StringBuilder info = new StringBuilder();
            info.append(prefix).append(name).append(" ").append(status).append(" ").append(mode);
            info.append(" §f- ").append(source.getName());
            
            // 显示版本信息（如果有）
            if (source.getVersion() != null) {
                info.append(" §7(").append(source.getVersion()).append(")");
            }
            
            context.getSource().sendFeedback(Text.literal(info.toString()));
            
            // 显示镜像信息
            if (!source.isApiMode() && source.getMirrorUrls() != null && source.getMirrorUrls().length > 0) {
                context.getSource().sendFeedback(Text.literal("  §7镜像地址: " + source.getMirrorUrls().length + " 个可用"));
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
            context.getSource().sendFeedback(Text.literal("§a成功切换到数据源：" + sourceName));
        } else {
            context.getSource().sendError(Text.literal("§c切换失败：数据源 '" + sourceName + "' 不存在或未启用"));
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
            String mode = source.isApiMode() ? "API模式" : "JSON模式";
            context.getSource().sendFeedback(Text.literal("§6当前数据源：§a" + currentSource + " §f- " + source.getName() + " §7(" + mode + ")"));
            
            if (source.isApiMode() && source.getApiBaseUrl() != null) {
                context.getSource().sendFeedback(Text.literal("§7API地址：" + source.getApiBaseUrl()));
            } else if (!source.isApiMode() && source.getUrl() != null) {
                context.getSource().sendFeedback(Text.literal("§7JSON地址：" + source.getUrl()));
            }
        } else {
            context.getSource().sendError(Text.literal("§c当前数据源配置异常"));
        }
        return 1;
    }
    
    /**
     * 检查所有数据源更新和状态
     */
    private static int checkAllSources(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.literal("§6正在检查所有数据源..."));
        
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
                        StringBuilder info = new StringBuilder();
                        info.append("§6").append(sourceName).append(" §f(API模式) ");
                        info.append(status.getStatusText());
                        
                        context.getSource().sendFeedback(Text.literal(info.toString()));
                    });
                });
            } else {
                // JSON模式：检查更新并下载
                com.fletime.toriifind.service.LocalDataService.checkAndUpdateDataSource(sourceName, dataSource)
                    .thenAcceptAsync(updated -> {
                        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                            StringBuilder info = new StringBuilder();
                            info.append("§6").append(sourceName).append(" §f(JSON模式) ");
                            
                            if (updated) {
                                info.append("§a[已更新]");
                            } else {
                                info.append("§a[最新版本]");
                            }
                            
                            // 显示版本信息（如果存在）
                            String version = com.fletime.toriifind.service.LocalDataService.getLocalVersion(
                                com.fletime.toriifind.service.LocalDataService.getLocalDataFile(sourceName));
                            if (version != null && !version.isEmpty()) {
                                info.append(" §7v").append(version);
                            }
                            
                            context.getSource().sendFeedback(Text.literal(info.toString()));
                            
                            // 如果有镜像，直接显示镜像状态
                            if (dataSource.getMirrorUrls() != null && dataSource.getMirrorUrls().length > 0) {
                                MirrorStatusService.checkAllMirrors(dataSource).thenAccept(mirrorStatuses -> {
                                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                        for (MirrorStatusService.MirrorStatus mirror : mirrorStatuses) {
                                            String prefix = mirror.isPrimary() ? "§b[主]" : "§7[镜像]";
                                            String statusIcon = mirror.isAvailable() ? "§a✓" : "§c✗";
                                            
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
                            context.getSource().sendError(Text.literal("§c" + sourceName + " 检查失败: " + throwable.getMessage()));
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
        context.getSource().sendFeedback(Text.literal("§6正在重新加载配置文件..."));
        
        boolean success = ToriiFind.reloadConfig();
        
        if (success) {
            context.getSource().sendFeedback(Text.literal("§a✓ 配置文件已成功重新加载"));
            
            // 显示当前数据源信息
            String currentSource = ToriiFind.getCurrentSourceName();
            SourceConfig.DataSource source = ToriiFind.getAllSources().get(currentSource);
            if (source != null) {
                String mode = source.isApiMode() ? "API模式" : "JSON模式";
                context.getSource().sendFeedback(Text.literal("§6当前数据源: §a" + currentSource + " §f(" + mode + ")"));
            }
        } else {
            context.getSource().sendError(Text.literal("§c✗ 配置文件重新加载失败，请检查配置文件格式"));
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
                context.getSource().sendFeedback(Text.literal("§6正在查询..."));
                
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
                    formattedText += " §7" + landmark.getCoordinates().toString();
                }
                
                // 添加状态信息
                if (!"Normal".equals(landmark.getStatus())) {
                    formattedText += " §c[" + landmark.getStatus() + "]";
                }
                
                MutableText baseText = Text.literal(formattedText + " ");
                String wikiUrl = "https://wiki.ria.red/wiki/" + landmark.getName();
                Style linkStyle = Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, wikiUrl))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                                  ToriiFind.translate("toriifind.result.wiki_hover", wikiUrl)))
                    .withFormatting(Formatting.UNDERLINE);
                MutableText linkText = ((MutableText)ToriiFind.translate("toriifind.result.wiki_link")).setStyle(linkStyle);
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
                context.getSource().sendFeedback(Text.literal("§6正在查询..."));
                
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
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, wikiUrl))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                        ToriiFind.translate("toriifind.result.wiki_hover", wikiUrl)))
                    .withFormatting(Formatting.UNDERLINE);

                MutableText linkText = ((MutableText) ToriiFind.translate("toriifind.result.wiki_link")).setStyle(linkStyle);
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
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, wikiUrl))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                        ToriiFind.translate("toriifind.result.wiki_hover", wikiUrl)))
                    .withFormatting(Formatting.UNDERLINE);

                MutableText linkText = ((MutableText) ToriiFind.translate("toriifind.result.wiki_link")).setStyle(linkStyle);
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
        // 首先尝试从本地文件读取
        Path localFile = com.fletime.toriifind.service.LocalDataService.getLocalDataFile("fletime");
        if (Files.exists(localFile)) {
            try {
                return loadZerothDataFromFile(localFile);
            } catch (Exception e) {
                System.err.println("[ToriiFind] 读取本地零洲数据失败，尝试从传统配置文件读取: " + e.getMessage());
            }
        }
        
        // 回退到传统配置文件
        Path configDir = FabricLoader.getInstance().getConfigDir();
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
        // 首先尝试从本地文件读取
        Path localFile = com.fletime.toriifind.service.LocalDataService.getLocalDataFile("fletime");
        if (Files.exists(localFile)) {
            try {
                return loadHoutuDataFromFile(localFile);
            } catch (Exception e) {
                System.err.println("[ToriiFind] 读取本地后土数据失败，尝试从传统配置文件读取: " + e.getMessage());
            }
        }
        
        // 回退到传统配置文件
        Path configDir = FabricLoader.getInstance().getConfigDir();
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
