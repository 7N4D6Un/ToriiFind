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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.help.command.ciallo"));
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        return 1;
    }

    /**
     * 按编号查找零洲鸟居
     */
    private static int searchZerothByNumber(CommandContext<FabricClientCommandSource> context, int number) {
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
     * 按名称或拼音查找零洲鸟居
     */
    private static int searchZerothByNameOrPinyin(CommandContext<FabricClientCommandSource> context, String keyword) {
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
     * 按编号查找后土境地
     */
    private static int searchHoutuByNumber(CommandContext<FabricClientCommandSource> context, String number) {
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
     * 展示零洲鸟居搜索结果
     */
    private static void displayZerothResults(CommandContext<FabricClientCommandSource> context, List<Torii> results) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        if (results.isEmpty()) {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.not_found"));
        } else {
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
                MutableText linkText = ((MutableText)ToriiFind.translate("toriifind.result.wiki_link")).setStyle(linkStyle);
                context.getSource().sendFeedback(baseText.append(linkText));
            }
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        }
    }

    /**
     * 展示后土境地搜索结果
     */
    private static void displayHoutuResults(CommandContext<FabricClientCommandSource> context, List<Houtu> results) {
        context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        if (results.isEmpty()) {
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.result.not_found"));
        } else {
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
                MutableText linkText = ((MutableText)ToriiFind.translate("toriifind.result.wiki_link")).setStyle(linkStyle);
                context.getSource().sendFeedback(baseText.append(linkText));
            }
            context.getSource().sendFeedback(ToriiFind.translate("toriifind.divider"));
        }
    }

    /**
     * 加载零洲数据
     * @return 零洲鸟居列表
     * @throws IOException 读取异常
     */
    private static List<Torii> loadZerothData() throws IOException {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("toriifind.json");
        List<Torii> toriiList = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(configFile)) {
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
     * 加载后土数据
     * @return 后土境地列表
     * @throws IOException 读取异常
     */
    private static List<Houtu> loadHoutuData() throws IOException {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("toriifind.json");
        List<Houtu> houtuList = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(configFile)) {
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
     * 彩蛋命令，往公屏发一条消息 Ciallo～(∠・ω< )⌒☆
     */
    private static int sendCialloMessage(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatMessage("Ciallo～(∠・ω< )⌒☆");
        }
        return 1;
    }
} 
