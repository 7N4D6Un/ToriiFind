package com.fletime.toriifind.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fletime.toriifind.config.SourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LynnApiService {
    
    public static class LynnLandmark {
        private final String id;
        private final String name;
        private final String grade;
        private final String status;
        private final Coordinates coordinates;
        
        public static class Coordinates {
            private final String x;
            private final String y;
            private final String z;
            
            public Coordinates(String x, String y, String z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
            
            public String getX() { return x; }
            public String getY() { return y; }
            public String getZ() { return z; }
            
            public boolean isUnknown() {
                return "Unknown".equals(x) || "Unknown".equals(y) || "Unknown".equals(z);
            }
            
            @Override
            public String toString() {
                if (isUnknown()) {
                    return "坐标未知";
                }
                return String.format("(%s, %s, %s)", x, y, z);
            }
        }
        
        public LynnLandmark(String id, String name, String grade, String status, Coordinates coordinates) {
            this.id = id;
            this.name = name;
            this.grade = grade;
            this.status = status;
            this.coordinates = coordinates;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getGrade() { return grade; }
        public String getStatus() { return status; }
        public Coordinates getCoordinates() { return coordinates; }
        
        @Override
        public String toString() {
            return id + " " + grade + " " + name + " " + coordinates.toString();
        }
    }
    
    /**
     * 通过API搜索landmark
     * @param apiBaseUrl API基础URL
     * @param source 数据源 (zth 或 houtu)
     * @param name 名称关键字（可选）
     * @return Landmark列表
     * @throws IOException 网络异常
     */
    public static List<LynnLandmark> searchLandmarks(String apiBaseUrl, String source, String name) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(apiBaseUrl);
        if (!apiBaseUrl.endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append("api/landmarks?source=").append(source);
        
        if (name != null && !name.trim().isEmpty()) {
            urlBuilder.append("&name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8));
        }
        
        String responseJson = makeHttpRequest(urlBuilder.toString());
        return parseLandmarksFromJson(responseJson);
    }
    
    /**
     * 通过ID获取单个landmark
     * @param apiBaseUrl API基础URL
     * @param source 数据源 (zth 或 houtu)
     * @param landmarkId landmark ID
     * @return Landmark对象
     * @throws IOException 网络异常
     */
    public static LynnLandmark getLandmarkById(String apiBaseUrl, String source, String landmarkId) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(apiBaseUrl);
        if (!urlBuilder.toString().endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append("api/landmarks/").append(landmarkId).append("?source=").append(source);
        
        String responseJson = makeHttpRequest(urlBuilder.toString());
        List<LynnLandmark> landmarks = parseLandmarksFromJson("[" + responseJson + "]");
        return landmarks.isEmpty() ? null : landmarks.get(0);
    }
    
    private static String makeHttpRequest(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Accept", "application/json");
        
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    
    private static List<LynnLandmark> parseLandmarksFromJson(String json) {
        List<LynnLandmark> landmarks = new ArrayList<>();
        
        try {
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
            
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject landmarkObj = jsonArray.get(i).getAsJsonObject();
                
                String id = landmarkObj.get("id").getAsString();
                String name = landmarkObj.get("name").getAsString();
                String grade = landmarkObj.get("grade").getAsString();
                String status = landmarkObj.has("status") ? landmarkObj.get("status").getAsString() : "Normal";
                
                // 解析坐标
                LynnLandmark.Coordinates coordinates = null;
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
        } catch (Exception e) {
            // 解析失败时返回空列表
        }
        
        return landmarks;
    }
}