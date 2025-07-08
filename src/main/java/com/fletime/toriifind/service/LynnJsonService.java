package com.fletime.toriifind.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fletime.toriifind.service.LynnApiService.LynnLandmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LynnJsonService {
    
    /**
     * 从数据源加载Lynn格式的数据（优先使用本地文件）
     * @param dataSource 数据源配置
     * @return Landmark列表
     * @throws IOException 网络或解析异常
     */
    public static List<LynnLandmark> loadFromDataSource(com.fletime.toriifind.config.SourceConfig.DataSource dataSource) throws IOException {
        // 首先尝试查找对应的本地文件
        for (java.util.Map.Entry<String, com.fletime.toriifind.config.SourceConfig.DataSource> entry : 
             com.fletime.toriifind.ToriiFind.getAllSources().entrySet()) {
            if (entry.getValue() == dataSource) {
                String sourceName = entry.getKey();
                Path localFile = LocalDataService.getLocalDataFile(sourceName);
                
                if (Files.exists(localFile)) {
                    try {
                        return loadFromFile(localFile);
                    } catch (Exception e) {
                        System.err.println("[ToriiFind] 读取本地文件失败，尝试从网络下载: " + e.getMessage());
                        break;
                    }
                }
                break;
            }
        }
        
        // 本地文件不存在或读取失败，从网络加载
        String[] urls = dataSource.getAllUrls();
        IOException lastException = null;
        
        for (String url : urls) {
            if (url == null) continue;
            
            try {
                String jsonContent = downloadJsonContent(url);
                return parseJsonContent(jsonContent);
            } catch (IOException e) {
                lastException = e;
                // 继续尝试下一个URL
            }
        }
        
        // 所有URL都失败了
        throw lastException != null ? lastException : new IOException("所有镜像地址都不可用");
    }
    
    /**
     * 从JSON文件URL加载Lynn格式的数据
     * @param jsonUrl JSON文件URL
     * @return Landmark列表
     * @throws IOException 网络或解析异常
     */
    public static List<LynnLandmark> loadFromUrl(String jsonUrl) throws IOException {
        String jsonContent = downloadJsonContent(jsonUrl);
        return parseJsonContent(jsonContent);
    }
    
    /**
     * 从本地文件加载Lynn格式的数据
     * @param filePath 本地文件路径
     * @return Landmark列表
     * @throws IOException 文件读取或解析异常
     */
    public static List<LynnLandmark> loadFromFile(Path filePath) throws IOException {
        try (Reader reader = Files.newBufferedReader(filePath)) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                content.append(buffer, 0, length);
            }
            return parseJsonContent(content.toString());
        }
    }
    
    private static String downloadJsonContent(String jsonUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    private static List<LynnLandmark> parseJsonContent(String jsonContent) {
        List<LynnLandmark> landmarks = new ArrayList<>();
        
        try {
            JsonObject rootObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            // 解析零洲数据
            if (rootObject.has("zeroth")) {
                JsonArray zerothArray = rootObject.getAsJsonArray("zeroth");
                landmarks.addAll(parseArrayToLandmarks(zerothArray));
            }
            
            // 解析后土数据  
            if (rootObject.has("houtu")) {
                JsonArray houtuArray = rootObject.getAsJsonArray("houtu");
                landmarks.addAll(parseArrayToLandmarks(houtuArray));
            }
        } catch (Exception e) {
            // 解析失败时返回空列表
        }
        
        return landmarks;
    }
    
    private static List<LynnLandmark> parseArrayToLandmarks(JsonArray jsonArray) {
        List<LynnLandmark> landmarks = new ArrayList<>();
        
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject landmarkObj = jsonArray.get(i).getAsJsonObject();
            
            String id = landmarkObj.get("id").getAsString();
            String name = landmarkObj.get("name").getAsString();
            String grade = landmarkObj.get("grade").getAsString();
            String status = landmarkObj.has("status") ? landmarkObj.get("status").getAsString() : "Normal";
            
            // 解析坐标
            LynnLandmark.Coordinates coordinates;
            if (landmarkObj.has("coordinates")) {
                JsonObject coordObj = landmarkObj.getAsJsonObject("coordinates");
                String x = coordObj.get("x").getAsString();
                String y = coordObj.get("y").getAsString();
                String z = coordObj.get("z").getAsString();
                coordinates = new LynnLandmark.Coordinates(x, y, z);
            } else {
                coordinates = new LynnLandmark.Coordinates("Unknown", "Unknown", "Unknown");
            }
            
            landmarks.add(new LynnLandmark(id, name, grade, status, coordinates));
        }
        
        return landmarks;
    }
    
    /**
     * 按名称或拼音过滤Landmark
     * @param landmarks 原始Landmark列表
     * @param keyword 关键字
     * @param toPinyinFunc 拼音转换函数
     * @return 过滤后的列表
     */
    public static List<LynnLandmark> filterByNameOrPinyin(List<LynnLandmark> landmarks, String keyword, java.util.function.Function<String, String> toPinyinFunc) {
        List<LynnLandmark> results = new ArrayList<>();
        
        // 首先按名称精确匹配
        for (LynnLandmark landmark : landmarks) {
            if (landmark.getName().contains(keyword)) {
                results.add(landmark);
            }
        }
        
        // 如果没有找到且关键字是字母，则按拼音搜索
        if (results.isEmpty() && keyword.matches("^[a-zA-Z]+$")) {
            String lowercaseKeyword = keyword.toLowerCase();
            for (LynnLandmark landmark : landmarks) {
                String namePinyin = toPinyinFunc.apply(landmark.getName()).toLowerCase();
                if (namePinyin.contains(lowercaseKeyword)) {
                    results.add(landmark);
                }
            }
        }
        
        return results;
    }
    
    /**
     * 按ID过滤Landmark
     * @param landmarks 原始Landmark列表
     * @param idKeyword ID关键字
     * @return 过滤后的列表
     */
    public static List<LynnLandmark> filterById(List<LynnLandmark> landmarks, String idKeyword) {
        List<LynnLandmark> results = new ArrayList<>();
        
        for (LynnLandmark landmark : landmarks) {
            if (landmark.getId().contains(idKeyword)) {
                results.add(landmark);
            }
        }
        
        return results;
    }
}