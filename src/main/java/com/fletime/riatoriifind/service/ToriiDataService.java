package com.fletime.riatoriifind.service;

import com.fletime.riatoriifind.RiaToriiFind;
import com.fletime.riatoriifind.config.ModConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ToriiDataService {

	private static HanyuPinyinOutputFormat PINYIN_FORMAT;
	private static JsonObject DATA_CACHE;

	private ToriiDataService() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static void reloadCache() {
		DATA_CACHE = null;
		if (ModConfig.debugMode) RiaToriiFind.LOGGER.info("数据缓存已清空");
	}

	// 后台预读数据，首次搜索时避免读文件卡顿
	public static void preloadAsync() {
		if (ModConfig.debugMode) RiaToriiFind.LOGGER.info("开始后台预加载数据");
		CompletableFuture.runAsync(() -> {
			try {
				parseLocalJson();
				if (ModConfig.debugMode && DATA_CACHE != null) RiaToriiFind.LOGGER.info("数据预加载完成");
			} catch (IOException e) {
			}
		});
	}

	public record ToriiEntry(String id, String name, String grade) {}
	public record HoutuEntry(String id, String name, String grade) {}
	public record FindEntry(String id, String name, String grade, String type) {}

	public static List<ToriiEntry> loadZeroth() throws IOException {
		return loadArray("zeroth", obj -> new ToriiEntry(
			getString(obj, "id"), getString(obj, "name"), getString(obj, "grade")));
	}

	public static List<HoutuEntry> loadHoutu() throws IOException {
		return loadArray("houtu", obj -> new HoutuEntry(
			getString(obj, "id"), getString(obj, "name"), getString(obj, "grade")));
	}

	private static <T> List<T> loadArray(String arrayKey, Function<JsonObject, T> mapper) throws IOException {
		var root = parseLocalJson();
		if (root == null || !root.has(arrayKey)) return List.of();
		var arr = root.getAsJsonArray(arrayKey);
		var list = new ArrayList<T>(arr.size());
		for (var el : arr) {
			list.add(mapper.apply(el.getAsJsonObject()));
		}
		return list;
	}

	public static List<ToriiEntry> searchZerothSmart(String query) throws IOException {
		var all = loadZeroth();
		if (query.matches("^\\d+$")) {
			return mergeById(filterById(all, query, ToriiEntry::id), filterByNameOrPinyin(all, query, ToriiEntry::name));
		}
		return filterByNameOrPinyin(all, query, ToriiEntry::name);
	}

	public static List<HoutuEntry> searchHoutuSmart(String query) throws IOException {
		var all = loadHoutu();
		if (query.matches("^\\d+$")) {
			return mergeById(filterById(all, query, HoutuEntry::id), filterByNameOrPinyin(all, query, HoutuEntry::name));
		}
		return filterByNameOrPinyin(all, query, HoutuEntry::name);
	}

	public static List<FindEntry> searchAll(String query) throws IOException {
		var results = new ArrayList<FindEntry>();
		for (var e : searchZerothSmart(query)) {
			results.add(new FindEntry(e.id(), e.name(), e.grade(), "zeroth"));
		}
		for (var e : searchHoutuSmart(query)) {
			results.add(new FindEntry(e.id(), e.name(), e.grade(), "houtu"));
		}
		if (ModConfig.debugMode) RiaToriiFind.LOGGER.info("搜索 \"{}\" 返回 {} 条结果", query, results.size());
		return results;
	}

	private static <T> List<T> filterById(List<T> list, String id, Function<T, String> idFn) {
		var result = new ArrayList<T>();
		for (var e : list) {
			if (idFn.apply(e).contains(id)) result.add(e);
		}
		return result;
	}

	private static <T> List<T> filterByNameOrPinyin(List<T> list, String keyword, Function<T, String> nameFn) {
		var result = new ArrayList<T>();
		for (var e : list) {
			if (nameFn.apply(e).contains(keyword)) {
				result.add(e);
			}
		}
		// 纯字母关键词才触发拼音匹配
		if (result.isEmpty() && keyword.matches("^[a-zA-Z]+$")) {
			var lower = keyword.toLowerCase();
			for (var e : list) {
				if (toPinyin(nameFn.apply(e)).toLowerCase().contains(lower)) {
					result.add(e);
				}
			}
		}
		return result;
	}

	private static <T> List<T> mergeById(List<T> primary, List<T> secondary) {
		var seen = new LinkedHashMap<String, T>();
		for (var e : primary) seen.put(keyOf(e), e);
		for (var e : secondary) seen.putIfAbsent(keyOf(e), e);
		return List.copyOf(seen.values());
	}

	@SuppressWarnings("unchecked")
	private static <T> String keyOf(T entry) {
		return switch (entry) {
			case ToriiEntry e -> e.id();
			case HoutuEntry e -> e.id();
			default -> throw new IllegalArgumentException();
		};
	}

	public static String toPinyin(String input) {
		if (input == null || input.isEmpty()) return "";

		if (PINYIN_FORMAT == null) {
			PINYIN_FORMAT = new HanyuPinyinOutputFormat();
			PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
			PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
		}

		var sb = new StringBuilder();
		for (char c : input.toCharArray()) {
			if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
				try {
					var arr = PinyinHelper.toHanyuPinyinStringArray(c, PINYIN_FORMAT);
					if (arr != null && arr.length > 0) sb.append(arr[0]);
				} catch (BadHanyuPinyinOutputFormatCombination e) {
					sb.append(c);
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	// 缓存读取：首次搜索或切换源后同步读文件，此后全内存
	private static JsonObject parseLocalJson() throws IOException {
		if (DATA_CACHE != null) return DATA_CACHE;
		var file = ModConfig.getLocalDataFile();
		if (!Files.exists(file)) return null;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			var el = JsonParser.parseReader(reader);
			DATA_CACHE = el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
			if (ModConfig.debugMode && DATA_CACHE != null) RiaToriiFind.LOGGER.info("数据已从文件加载到缓存: {}", file);
			return DATA_CACHE;
		}
	}

	private static String getString(JsonObject obj, String key) {
		return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
	}
}
