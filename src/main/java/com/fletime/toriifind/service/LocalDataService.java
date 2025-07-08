package com.fletime.toriifind.service;

import com.fletime.toriifind.config.SourceConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LocalDataService {
    
    /**
     * 获取本地数据目录
     */
    public static Path getLocalDataDir() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve("toriifind");
    }
    
    /**
     * 获取指定数据源的本地文件路径
     */
    public static Path getLocalDataFile(String sourceName) {
        return getLocalDataDir().resolve(sourceName + ".json");
    }
    
    /**
     * 初始化所有数据源到本地
     */
    public static CompletableFuture<Void> initializeAllDataSources(Map<String, SourceConfig.DataSource> sources) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 确保目录存在
                Files.createDirectories(getLocalDataDir());
                
                for (Map.Entry<String, SourceConfig.DataSource> entry : sources.entrySet()) {
                    String sourceName = entry.getKey();
                    SourceConfig.DataSource source = entry.getValue();
                    
                    // 只下载JSON类型的数据源
                    if (!source.isApiMode() && source.getUrl() != null) {
                        try {
                            downloadDataSource(sourceName, source);
                            System.out.println("[ToriiFind] 已下载数据源: " + sourceName);
                        } catch (Exception e) {
                            System.err.println("[ToriiFind] 下载数据源失败 " + sourceName + ": " + e.getMessage());
                            // 尝试镜像URL
                            if (source.getMirrorUrls() != null) {
                                for (String mirrorUrl : source.getMirrorUrls()) {
                                    try {
                                        downloadFromUrl(mirrorUrl, getLocalDataFile(sourceName));
                                        System.out.println("[ToriiFind] 通过镜像下载数据源成功: " + sourceName);
                                        break;
                                    } catch (Exception me) {
                                        System.err.println("[ToriiFind] 镜像下载失败: " + me.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }
                
                System.out.println("[ToriiFind] 数据源初始化完成");
            } catch (Exception e) {
                System.err.println("[ToriiFind] 数据源初始化失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 下载单个数据源
     */
    public static void downloadDataSource(String sourceName, SourceConfig.DataSource source) throws IOException {
        if (source.isApiMode() || source.getUrl() == null) {
            return;
        }
        
        Path localFile = getLocalDataFile(sourceName);
        downloadFromUrl(source.getUrl(), localFile);
    }
    
    /**
     * 从URL下载文件
     */
    private static void downloadFromUrl(String url, Path targetFile) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "ToriiFind-Mod/1.0");
        
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * 检查本地文件是否存在
     */
    public static boolean isLocalDataExists(String sourceName) {
        return Files.exists(getLocalDataFile(sourceName));
    }
    
    /**
     * 检查并更新数据源
     */
    public static CompletableFuture<Boolean> checkAndUpdateDataSource(String sourceName, SourceConfig.DataSource source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (source.isApiMode() || source.getUrl() == null) {
                    return false;
                }
                
                // 检查远程版本
                String remoteVersion = getRemoteVersion(source.getUrl());
                Path localFile = getLocalDataFile(sourceName);
                
                if (!Files.exists(localFile)) {
                    // 本地文件不存在，直接下载
                    downloadDataSource(sourceName, source);
                    return true;
                }
                
                // 比较版本
                String localVersion = getLocalVersion(localFile);
                if (remoteVersion != null && !remoteVersion.equals(localVersion)) {
                    // 版本不同，更新本地文件
                    downloadDataSource(sourceName, source);
                    System.out.println("[ToriiFind] 已更新数据源: " + sourceName + " 版本: " + remoteVersion);
                    return true;
                }
                
                return false;
            } catch (Exception e) {
                System.err.println("[ToriiFind] 检查更新失败 " + sourceName + ": " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 获取远程版本号
     */
    private static String getRemoteVersion(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Range", "bytes=0-2047"); // 读取更多内容以确保找到正确的version字段
            
            if (conn.getResponseCode() == 200 || conn.getResponseCode() == 206) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[2048];
                    int bytesRead = in.read(buffer);
                    String content = new String(buffer, 0, bytesRead, java.nio.charset.StandardCharsets.UTF_8);
                    
                    return extractRootVersion(content);
                }
            }
        } catch (Exception e) {
            // 忽略版本检查错误
        }
        return null;
    }
    
    /**
     * 获取本地文件版本号
     */
    public static String getLocalVersion(Path localFile) {
        try {
            byte[] buffer = new byte[2048];
            try (InputStream in = Files.newInputStream(localFile)) {
                int bytesRead = in.read(buffer);
                String content = new String(buffer, 0, bytesRead, java.nio.charset.StandardCharsets.UTF_8);
                
                return extractRootVersion(content);
            }
        } catch (Exception e) {
            // 忽略版本读取错误
        }
        return null;
    }
    
    /**
     * 从JSON内容中提取根级别的version字段
     */
    private static String extractRootVersion(String content) {
        try {
            // 使用Gson解析JSON以确保只获取根级别的version
            com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (jsonObject.has("version")) {
                com.google.gson.JsonElement versionElement = jsonObject.get("version");
                if (versionElement.isJsonPrimitive()) {
                    // 处理数字或字符串类型的版本号
                    return versionElement.getAsString();
                }
            }
        } catch (Exception e) {
            // JSON解析失败，使用简单字符串匹配作为fallback
            // 查找 "version": 在JSON开头附近，避免获取嵌套对象中的version
            int jsonStart = content.indexOf("{");
            if (jsonStart >= 0) {
                // 只在JSON开始后的前500个字符内查找
                String jsonHead = content.substring(jsonStart, Math.min(content.length(), jsonStart + 500));
                
                if (jsonHead.contains("\"version\"")) {
                    int versionIndex = jsonHead.indexOf("\"version\":");
                    // 确保这个version在第一层级（检查前面是否有嵌套的大括号）
                    String beforeVersion = jsonHead.substring(0, versionIndex);
                    long openBraces = beforeVersion.chars().filter(ch -> ch == '{').count();
                    long closeBraces = beforeVersion.chars().filter(ch -> ch == '}').count();
                    
                    // 如果大括号平衡，说明我们在根级别
                    if (openBraces == closeBraces + 1) {
                        int valueStart = versionIndex + 10; // "version":的长度
                        // 跳过空格和冒号
                        while (valueStart < jsonHead.length() && (jsonHead.charAt(valueStart) == ' ' || jsonHead.charAt(valueStart) == ':')) {
                            valueStart++;
                        }
                        
                        if (valueStart < jsonHead.length()) {
                            char firstChar = jsonHead.charAt(valueStart);
                            if (firstChar == '"') {
                                // 字符串版本号
                                int start = valueStart + 1;
                                int end = jsonHead.indexOf("\"", start);
                                if (end > start) {
                                    return jsonHead.substring(start, end);
                                }
                            } else if (Character.isDigit(firstChar)) {
                                // 数字版本号
                                int end = valueStart;
                                while (end < jsonHead.length() && 
                                       (Character.isDigit(jsonHead.charAt(end)) || jsonHead.charAt(end) == '.')) {
                                    end++;
                                }
                                if (end > valueStart) {
                                    return jsonHead.substring(valueStart, end);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}