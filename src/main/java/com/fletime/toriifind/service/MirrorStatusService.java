package com.fletime.toriifind.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fletime.toriifind.config.SourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MirrorStatusService {
    
    public static class MirrorStatus {
        private final String url;
        private final boolean available;
        private final String version;
        private final long responseTime;
        private final String error;
        private final boolean isPrimary;
        
        public MirrorStatus(String url, boolean available, String version, long responseTime, String error, boolean isPrimary) {
            this.url = url;
            this.available = available;
            this.version = version;
            this.responseTime = responseTime;
            this.error = error;
            this.isPrimary = isPrimary;
        }
        
        public String getUrl() { return url; }
        public boolean isAvailable() { return available; }
        public String getVersion() { return version; }
        public long getResponseTime() { return responseTime; }
        public String getError() { return error; }
        public boolean isPrimary() { return isPrimary; }
        
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
        
        public String getUrlDisplayName() {
            if (url.contains("github.com")) {
                return "GitHub";
            } else if (url.contains("kkgithub.com")) {
                return "KK镜像";
            } else if (url.contains("jsdelivr.net")) {
                return "JSDelivr";
            } else if (url.contains("fastly.")) {
                return "Fastly";
            } else {
                // 提取域名
                try {
                    String domain = new URL(url).getHost();
                    return domain.replaceAll("^www\\.", "");
                } catch (Exception e) {
                    return "镜像站";
                }
            }
        }
    }
    
    /**
     * 检查所有镜像的状态
     */
    public static CompletableFuture<List<MirrorStatus>> checkAllMirrors(SourceConfig.DataSource dataSource) {
        List<CompletableFuture<MirrorStatus>> futures = new ArrayList<>();
        String[] allUrls = dataSource.getAllUrls();
        
        for (int i = 0; i < allUrls.length; i++) {
            String url = allUrls[i];
            boolean isPrimary = (i == 0);
            
            CompletableFuture<MirrorStatus> future = CompletableFuture.supplyAsync(() -> {
                return checkSingleMirror(url, isPrimary);
            }).orTimeout(3, TimeUnit.SECONDS)
            .handle((status, throwable) -> {
                if (throwable != null) {
                    return new MirrorStatus(url, false, null, 0, "检测超时", isPrimary);
                }
                return status;
            });
            
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<MirrorStatus> results = new ArrayList<>();
                    for (CompletableFuture<MirrorStatus> future : futures) {
                        try {
                            results.add(future.get());
                        } catch (Exception e) {
                            // 应该不会发生，因为我们已经处理了异常
                        }
                    }
                    return results;
                });
    }
    
    /**
     * 检查单个镜像状态
     */
    private static MirrorStatus checkSingleMirror(String url, boolean isPrimary) {
        if (url == null) {
            return new MirrorStatus(url, false, null, 0, "URL为空", isPrimary);
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 首先用HEAD请求检查可用性
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            
            int responseCode = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (responseCode == 200) {
                // 获取版本信息
                String version = getVersionFromUrl(url);
                return new MirrorStatus(url, true, version, responseTime, null, isPrimary);
            } else {
                return new MirrorStatus(url, false, null, responseTime, "HTTP " + responseCode, isPrimary);
            }
        } catch (Exception e) {
            return new MirrorStatus(url, false, null, 0, e.getMessage(), isPrimary);
        }
    }
    
    /**
     * 从URL获取版本信息
     */
    private static String getVersionFromUrl(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            
            // 只读取前1KB来获取版本信息
            conn.setRequestProperty("Range", "bytes=0-1023");
            
            if (conn.getResponseCode() == 200 || conn.getResponseCode() == 206) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead = in.read(buffer);
                    String content = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    
                    // 查找版本号
                    if (content.contains("\"version\"")) {
                        try {
                            // 尝试解析JSON
                            int startIndex = content.indexOf("{");
                            if (startIndex >= 0) {
                                // 找到第一个完整的JSON对象
                                int braceCount = 0;
                                int endIndex = startIndex;
                                for (int i = startIndex; i < content.length(); i++) {
                                    char c = content.charAt(i);
                                    if (c == '{') braceCount++;
                                    else if (c == '}') braceCount--;
                                    
                                    if (braceCount == 0) {
                                        endIndex = i + 1;
                                        break;
                                    }
                                }
                                
                                String jsonPart = content.substring(startIndex, endIndex);
                                JsonObject jsonObject = JsonParser.parseString(jsonPart).getAsJsonObject();
                                
                                if (jsonObject.has("version")) {
                                    return "v" + jsonObject.get("version").getAsString();
                                }
                            }
                        } catch (Exception e) {
                            // JSON解析失败，忽略
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略版本获取错误
        }
        return null;
    }
    
    /**
     * 获取最佳可用镜像
     */
    public static MirrorStatus getBestMirror(List<MirrorStatus> mirrors) {
        MirrorStatus best = null;
        long bestTime = Long.MAX_VALUE;
        
        for (MirrorStatus mirror : mirrors) {
            if (mirror.isAvailable() && mirror.getResponseTime() < bestTime) {
                best = mirror;
                bestTime = mirror.getResponseTime();
            }
        }
        
        return best;
    }
}