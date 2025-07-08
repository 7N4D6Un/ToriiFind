package com.fletime.toriifind.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fletime.toriifind.config.SourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SourceStatusService {
    
    public static class SourceStatus {
        private final boolean available;
        private final String version;
        private final long responseTime;
        private final String error;
        
        public SourceStatus(boolean available, String version, long responseTime, String error) {
            this.available = available;
            this.version = version;
            this.responseTime = responseTime;
            this.error = error;
        }
        
        public boolean isAvailable() { return available; }
        public String getVersion() { return version; }
        public long getResponseTime() { return responseTime; }
        public String getError() { return error; }
        
        public String getStatusText() {
            if (available) {
                String status = "§a[在线]";
                if (responseTime > 0) {
                    status += String.format(" §7(%dms)", responseTime);
                }
                return status;
            } else {
                return "§c[离线]" + (error != null ? " §7(" + error + ")" : "");
            }
        }
    }
    
    /**
     * 检查数据源状态
     * @param dataSource 数据源配置
     * @return 状态信息
     */
    public static SourceStatus checkSourceStatus(SourceConfig.DataSource dataSource) {
        if (dataSource.isApiMode()) {
            return checkApiStatus(dataSource.getApiBaseUrl());
        } else {
            return checkBestJsonUrl(dataSource);
        }
    }
    
    /**
     * 检查API模式状态
     */
    private static SourceStatus checkApiStatus(String apiBaseUrl) {
        if (apiBaseUrl == null) {
            return new SourceStatus(false, null, 0, "未配置API地址");
        }
        
        try {
            String healthUrl = apiBaseUrl;
            if (!healthUrl.endsWith("/")) {
                healthUrl += "/";
            }
            healthUrl += "api/landmarks?source=zth";
            
            long startTime = System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) new URL(healthUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);  // 减少到2秒
            conn.setReadTimeout(2000);     // 减少到2秒
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (responseCode == 200) {
                // 尝试获取版本信息
                String version = getApiVersion(apiBaseUrl);
                return new SourceStatus(true, version, responseTime, null);
            } else {
                return new SourceStatus(false, null, responseTime, "HTTP " + responseCode);
            }
        } catch (Exception e) {
            return new SourceStatus(false, null, 0, e.getMessage());
        }
    }
    
    /**
     * 检查JSON模式状态，找到最佳可用URL
     */
    private static SourceStatus checkBestJsonUrl(SourceConfig.DataSource dataSource) {
        String[] urls = dataSource.getAllUrls();
        
        SourceStatus bestStatus = null;
        long bestResponseTime = Long.MAX_VALUE;
        
        for (String url : urls) {
            if (url == null) continue;
            
            SourceStatus status = checkJsonUrl(url);
            if (status.isAvailable() && status.getResponseTime() < bestResponseTime) {
                bestStatus = status;
                bestResponseTime = status.getResponseTime();
            }
            
            // 如果找到快速响应的URL，直接返回
            if (status.isAvailable() && status.getResponseTime() < 1000) {
                return status;
            }
        }
        
        // 如果所有URL都不可用，返回第一个URL的状态
        return bestStatus != null ? bestStatus : checkJsonUrl(urls[0]);
    }
    
    /**
     * 检查单个JSON URL状态
     */
    private static SourceStatus checkJsonUrl(String jsonUrl) {
        if (jsonUrl == null) {
            return new SourceStatus(false, null, 0, "未配置URL");
        }
        
        try {
            long startTime = System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
            conn.setRequestMethod("HEAD");  // 使用HEAD请求减少流量
            conn.setConnectTimeout(2000);   // 减少到2秒
            conn.setReadTimeout(2000);      // 减少到2秒
            
            int responseCode = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (responseCode == 200) {
                // 获取版本信息
                String version = getJsonVersion(jsonUrl);
                return new SourceStatus(true, version, responseTime, null);
            } else {
                return new SourceStatus(false, null, responseTime, "HTTP " + responseCode);
            }
        } catch (Exception e) {
            return new SourceStatus(false, null, 0, e.getMessage());
        }
    }
    
    /**
     * 获取JSON源版本信息
     */
    private static String getJsonVersion(String jsonUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1500);  // 减少版本检测超时
            conn.setReadTimeout(1500);
            
            try (InputStream in = conn.getInputStream()) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
                
                if (jsonObject.has("version")) {
                    return "v" + jsonObject.get("version").getAsString();
                }
            }
        } catch (Exception e) {
            // 忽略版本获取错误
        }
        return null;
    }
    
    /**
     * 获取API源版本信息
     */
    private static String getApiVersion(String apiBaseUrl) {
        try {
            String versionUrl = apiBaseUrl;
            if (!versionUrl.endsWith("/")) {
                versionUrl += "/";
            }
            versionUrl += "version";  // 假设API有版本端点
            
            HttpURLConnection conn = (HttpURLConnection) new URL(versionUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1500);  // 减少版本检测超时
            conn.setReadTimeout(1500);
            conn.setRequestProperty("Accept", "application/json");
            
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
                    
                    if (jsonObject.has("version")) {
                        return "v" + jsonObject.get("version").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            // 忽略版本获取错误
        }
        return null;
    }
    
    /**
     * 获取所有数据源的状态
     */
    public static Map<String, SourceStatus> getAllSourcesStatus(Map<String, SourceConfig.DataSource> sources) {
        Map<String, SourceStatus> statusMap = new HashMap<>();
        
        for (Map.Entry<String, SourceConfig.DataSource> entry : sources.entrySet()) {
            String sourceName = entry.getKey();
            SourceConfig.DataSource dataSource = entry.getValue();
            
            if (dataSource.isEnabled()) {
                statusMap.put(sourceName, checkSourceStatus(dataSource));
            } else {
                statusMap.put(sourceName, new SourceStatus(false, null, 0, "已禁用"));
            }
        }
        
        return statusMap;
    }
}