package com.fletime.toriifind;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fletime.toriifind.config.SourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ToriiFind implements ClientModInitializer {
	public static final String MOD_ID = "toriifind";
	public static final int CONFIG_VERSION = 7; // 当前内置的数据版本
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private static SourceConfig sourceConfig;

	@Override
	public void onInitializeClient() {
		sourceConfig = SourceConfig.loadOrCreateDefault();
		createOrUpdateConfigFile();
		ToriiFindCommand.register();
		
		// 异步初始化和更新数据源
		initializeAndUpdateDataSources();
	}
	
	/**
	 * 初始化和更新数据源
	 */
	private void initializeAndUpdateDataSources() {
		com.fletime.toriifind.service.LocalDataService.initializeAllDataSources(sourceConfig.getSources())
			.thenCompose(v -> {
				// 初始化完成后，检查所有数据源的更新
				LOGGER.info("[ToriiFind] 正在检查数据源更新...");
				
				java.util.List<java.util.concurrent.CompletableFuture<Boolean>> updateTasks = 
					new java.util.ArrayList<>();
				
				for (java.util.Map.Entry<String, com.fletime.toriifind.config.SourceConfig.DataSource> entry : 
					 sourceConfig.getSources().entrySet()) {
					String sourceName = entry.getKey();
					com.fletime.toriifind.config.SourceConfig.DataSource dataSource = entry.getValue();
					
					// 只检查JSON类型的数据源更新
					if (!dataSource.isApiMode()) {
						java.util.concurrent.CompletableFuture<Boolean> updateTask = 
							com.fletime.toriifind.service.LocalDataService.checkAndUpdateDataSource(sourceName, dataSource)
								.handle((updated, throwable) -> {
									if (throwable != null) {
										LOGGER.warn("[ToriiFind] 检查 {} 更新失败: {}", sourceName, throwable.getMessage());
										return false;
									}
									if (updated) {
										LOGGER.info("[ToriiFind] {} 已更新到最新版本", sourceName);
									}
									return updated;
								});
						updateTasks.add(updateTask);
					}
				}
				
				// 等待所有更新任务完成
				return java.util.concurrent.CompletableFuture.allOf(
					updateTasks.toArray(new java.util.concurrent.CompletableFuture[0])
				);
			})
			.thenRun(() -> {
				LOGGER.info("[ToriiFind] 数据源初始化和更新检查完成");
			})
			.exceptionally(throwable -> {
				LOGGER.error("[ToriiFind] 数据源初始化失败: " + throwable.getMessage());
				return null;
			});
	}
	
	public static SourceConfig getSourceConfig() {
		return sourceConfig;
	}
	
	public static String getCurrentSourceUrl() {
		SourceConfig.DataSource current = sourceConfig.getCurrentDataSource();
		return current != null ? current.getUrl() : null;
	}
	
	public static boolean switchSource(String sourceName) {
		if (sourceConfig.getSources().containsKey(sourceName)) {
			SourceConfig.DataSource source = sourceConfig.getSources().get(sourceName);
			if (source.isEnabled()) {
				sourceConfig.setCurrentSource(sourceName);
				sourceConfig.safeSave(); // 使用安全保存
				return true;
			}
		}
		return false;
	}
	
	public static String getCurrentSourceName() {
		return sourceConfig.getCurrentSource();
	}
	
	public static Map<String, SourceConfig.DataSource> getAllSources() {
		return sourceConfig.getSources();
	}
	
	/**
	 * 重新加载配置文件
	 */
	public static boolean reloadConfig() {
		try {
			SourceConfig newConfig = SourceConfig.loadOrCreateDefault();
			sourceConfig = newConfig;
			LOGGER.info("[ToriiFind] 配置文件已重新加载");
			return true;
		} catch (Exception e) {
			LOGGER.error("[ToriiFind] 配置文件重载失败: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * 检查本地配置文件是否存在或版本过低，必要时释放默认配置。
	 * 然后与云端配置文件比对版本，自动下载新版本覆盖本地。
	 */
	private void createOrUpdateConfigFile() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configFile = configDir.resolve("toriifind.json");
		
		boolean shouldUpdateConfig = false;
		
		// 检查本地配置文件是否存在或版本过低
		if (!Files.exists(configFile)) {
			shouldUpdateConfig = true;
		} else {
			try {
				int fileVersion = getConfigFileVersion(configFile);
				if (fileVersion < CONFIG_VERSION) {
					shouldUpdateConfig = true;
				}
			} catch (Exception e) {
				shouldUpdateConfig = true;
			}
		}
		
		// 释放默认配置文件
		if (shouldUpdateConfig) {
			try {
				Files.createDirectories(configDir);
				
				InputStream defaultConfigStream = ToriiFind.class.getClassLoader()
						.getResourceAsStream("assets/" + MOD_ID + "/toriifind.json");
				
				if (defaultConfigStream != null) {
					Files.copy(defaultConfigStream, configFile, StandardCopyOption.REPLACE_EXISTING);
					defaultConfigStream.close();
				}
			} catch (IOException e) {
				// 忽略异常，后续有日志输出
			}
		}

		// 检查云端配置文件版本
		String currentSourceUrl = getCurrentSourceUrl();
		if (currentSourceUrl != null) {
			try {
				int localVersion = getConfigFileVersion(configFile);
				int serverVersion = fetchServerConfigVersion(currentSourceUrl);
				if (serverVersion > localVersion) {
					LOGGER.info("[ToriiFind] 检测到云端配置文件有新版本，正在下载...");
					downloadServerConfig(configFile, currentSourceUrl);
					LOGGER.info("[ToriiFind] 云端配置文件已更新到最新版本。");
				}
			} catch (Exception e) {
				LOGGER.warn("[ToriiFind] 检查或下载云端配置文件失败：" + e.getMessage());
			}
		}
	}
	
	/**
	 * 获取本地配置文件的 version 字段
	 * @param configFile 配置文件路径
	 * @return 版本号（无 version 字段时返回 0）
	 * @throws IOException 读取异常
	 */
	private int getConfigFileVersion(Path configFile) throws IOException {
		try (Reader reader = Files.newBufferedReader(configFile)) {
			JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
			if (jsonObject.has("version")) {
				return jsonObject.get("version").getAsInt();
			}
			return 0; // 如果没有版本号，返回0，表示版本最低
		}
	}

	/**
	 * 获取云端配置文件的 version 字段
	 * @param serverUrl 云端配置文件URL
	 * @return 云端配置文件版本号（无 version 字段时返回 0）
	 * @throws IOException 网络或解析异常
	 */
	private int fetchServerConfigVersion(String serverUrl) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(serverUrl).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		try (InputStream in = conn.getInputStream()) {
			String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
			if (jsonObject.has("version")) {
				return jsonObject.get("version").getAsInt();
			}
			return 0;
		}
	}

	/**
	 * 下载云端配置文件并覆盖本地配置文件
	 * @param configFile 本地配置文件路径
	 * @param serverUrl 云端配置文件URL
	 * @throws IOException 网络或写入异常
	 */
	private void downloadServerConfig(Path configFile, String serverUrl) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(serverUrl).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		try (InputStream in = conn.getInputStream()) {
			Files.copy(in, configFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}
	
	/**
	 * 多语言文本转换（单参数）
	 * @param key 语言键
	 * @return 可翻译文本
	 */
	public static Text translate(String key) {
		return Text.translatable(key);
	}
	
	/**
	 * 多语言文本转换（多参数）
	 * @param key 语言键
	 * @param args 参数
	 * @return 可翻译文本
	 */
	public static Text translate(String key, Object... args) {
		return Text.translatable(key, args);
	}
}
