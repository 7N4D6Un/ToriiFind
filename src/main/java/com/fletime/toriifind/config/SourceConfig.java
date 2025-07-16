package com.fletime.toriifind.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SourceConfig {
    private Map<String, DataSource> sources = new HashMap<>();
    private String currentSource = "fletime";
    private int version = 1;
    
    public static class DataSource {
        private String name;
        private String url;
        private boolean enabled = true;
        private SourceType type = SourceType.JSON;
        private String apiBaseUrl;
        private String[] mirrorUrls;
        private String version;
        
        public enum SourceType {
            JSON,    // 传统JSON文件模式
            API      // Lynn源API模式
        }
        
        public DataSource() {}
        
        public DataSource(String name, String url, boolean enabled) {
            this.name = name;
            this.url = url;
            this.enabled = enabled;
            this.type = SourceType.JSON;
        }
        
        public DataSource(String name, String url, boolean enabled, SourceType type, String apiBaseUrl) {
            this.name = name;
            this.url = url;
            this.enabled = enabled;
            this.type = type;
            this.apiBaseUrl = apiBaseUrl;
        }
        
        public DataSource(String name, String url, boolean enabled, String[] mirrorUrls, String version) {
            this.name = name;
            this.url = url;
            this.enabled = enabled;
            this.type = SourceType.JSON;
            this.mirrorUrls = mirrorUrls;
            this.version = version;
        }
        
        public DataSource(String name, String url, boolean enabled, SourceType type, String apiBaseUrl, String version) {
            this.name = name;
            this.url = url;
            this.enabled = enabled;
            this.type = type;
            this.apiBaseUrl = apiBaseUrl;
            this.version = version;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public SourceType getType() { return type; }
        public void setType(SourceType type) { this.type = type; }
        
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
        
        public String[] getMirrorUrls() { return mirrorUrls; }
        public void setMirrorUrls(String[] mirrorUrls) { this.mirrorUrls = mirrorUrls; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public boolean isApiMode() { return type == SourceType.API; }
        
        public String[] getAllUrls() {
            if (mirrorUrls == null || mirrorUrls.length == 0) {
                return new String[]{url};
            }
            String[] allUrls = new String[mirrorUrls.length + 1];
            allUrls[0] = url;
            System.arraycopy(mirrorUrls, 0, allUrls, 1, mirrorUrls.length);
            return allUrls;
        }
    }
    
    public Map<String, DataSource> getSources() { return sources; }
    public void setSources(Map<String, DataSource> sources) { this.sources = sources; }
    
    public String getCurrentSource() { return currentSource; }
    public void setCurrentSource(String currentSource) { this.currentSource = currentSource; }
    
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    
    public DataSource getCurrentDataSource() {
        return sources.get(currentSource);
    }
    
    public static SourceConfig loadOrCreateDefault() {
        Path configFile = getConfigPath();
        
        // 确保配置目录存在
        try {
            Files.createDirectories(configFile.getParent());
        } catch (Exception e) {
            System.err.println("[ToriiFind] 创建配置目录失败: " + e.getMessage());
        }
        
        if (!Files.exists(configFile)) {
            SourceConfig config = createDefaultConfig();
            config.save(); // 只在文件不存在时保存
            return config;
        }
        
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            Constructor constructor = new Constructor(SourceConfig.class, loaderOptions);
            Yaml yaml = new Yaml(constructor);
            try (FileInputStream inputStream = new FileInputStream(configFile.toFile())) {
                SourceConfig config = yaml.load(inputStream);
                if (config == null) {
                    // 文件存在但为空，创建备份后重新创建
                    backupConfigFile();
                    SourceConfig newConfig = createDefaultConfig();
                    newConfig.save();
                    return newConfig;
                }
                
                // 验证配置完整性，如果缺少关键字段则补充
                if (config.sources == null) {
                    config.sources = new HashMap<>();
                }
                
                // 检查是否需要添加新的默认数据源（用于版本升级）
                addMissingDefaultSources(config);
                
                return config;
            }
        } catch (Exception e) {
            // 解析失败，创建备份后重新创建
            System.err.println("[ToriiFind] 配置文件解析失败: " + e.getMessage());
            System.err.println("[ToriiFind] 这可能是由于配置文件格式错误或损坏导致的");
            backupConfigFile();
            
            // 询问用户是否要重置配置
            System.out.println("[ToriiFind] 将创建新的默认配置文件，原配置已备份");
            
            SourceConfig newConfig = createDefaultConfig();
            newConfig.save();
            return newConfig;
        }
    }
    
    /**
     * 验证配置文件格式
     */
    public static boolean validateConfig() {
        Path configFile = getConfigPath();
        if (!Files.exists(configFile)) {
            return false;
        }
        
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            Constructor constructor = new Constructor(SourceConfig.class, loaderOptions);
            Yaml yaml = new Yaml(constructor);
            try (FileInputStream inputStream = new FileInputStream(configFile.toFile())) {
                SourceConfig config = yaml.load(inputStream);
                return config != null && config.sources != null && !config.sources.isEmpty();
            }
        } catch (Exception e) {
            System.err.println("[ToriiFind] 配置文件验证失败: " + e.getMessage());
            return false;
        }
    }
    
    private static SourceConfig createDefaultConfig() {
        SourceConfig config = new SourceConfig();
        // 添加本地数据源
        config.sources.put("local", new DataSource(
            "本地数据源",
            null,
            true
        ));
        // 添加默认的fletime源
        config.sources.put("fletime", new DataSource(
            "FleTime源",
            "https://wiki.ria.red/wiki/%E7%94%A8%E6%88%B7:FleTime/toriifind.json?action=raw",
            true
        ));
        
        // 添加lynn源（JSON模式，包含镜像）
        config.sources.put("lynn-json", new DataSource(
            "Lynn源 (JSON模式)",
            "https://github.com/7N4D6Un/ToriiFind/raw/refs/heads/main/data/lynn.json",
            true,
            new String[]{
                "https://raw.kkgithub.com/7N4D6Un/ToriiFind/main/data/lynn.json",
                "https://fastly.jsdelivr.net/gh/7N4D6Un/ToriiFind@main/data/lynn.json"
            },
            null
        ));
        
        // 添加lynn源（在线API模式）
        config.sources.put("lynn-api", new DataSource(
            "Lynn源 (API模式)",
            null,
            true,
            DataSource.SourceType.API,
            "https://ria-data.api.lynn6.top",
            null
        ));
        
        config.currentSource = "fletime";
        config.version = 2;
        
        // 不再自动保存，由调用者决定是否保存
        
        return config;
    }
    
    /**
     * 备份现有配置文件
     */
    private static void backupConfigFile() {
        try {
            Path configFile = getConfigPath();
            if (Files.exists(configFile)) {
                Path backupFile = configFile.getParent().resolve("config.yml.backup");
                Files.copy(configFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[ToriiFind] 已备份配置文件到: " + backupFile);
            }
        } catch (Exception e) {
            System.err.println("[ToriiFind] 备份配置文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 为现有配置添加缺失的默认数据源（用于版本升级）
     * 只在版本升级时才会添加新的数据源
     */
    private static void addMissingDefaultSources(SourceConfig config) {
        boolean needsSave = false;
        
        // 只有在版本升级时才添加新的数据源
        // 如果用户删除了某个数据源，我们不应该自动添加回来
        if (config.version < 2) {
            // 检查并添加fletime源
            if (!config.sources.containsKey("fletime")) {
                config.sources.put("fletime", new DataSource(
                    "FleTime源",
                    "https://wiki.ria.red/wiki/%E7%94%A8%E6%88%B7:FleTime/toriifind.json?action=raw",
                    true
                ));
                needsSave = true;
            }
            
            // 检查并添加lynn-json源
            if (!config.sources.containsKey("lynn-json")) {
                config.sources.put("lynn-json", new DataSource(
                    "Lynn源 (JSON模式)",
                    "https://github.com/7N4D6Un/ToriiFind/raw/refs/heads/main/data/lynn.json",
                    true,
                    new String[]{
                        "https://raw.kkgithub.com/7N4D6Un/ToriiFind/main/data/lynn.json",
                        "https://fastly.jsdelivr.net/gh/7N4D6Un/ToriiFind@main/data/lynn.json"
                    },
                    null
                ));
                needsSave = true;
            }
            
            // 检查并添加lynn-api源
            if (!config.sources.containsKey("lynn-api")) {
                config.sources.put("lynn-api", new DataSource(
                    "Lynn源 (API模式)",
                    null,
                    true,
                    DataSource.SourceType.API,
                    "https://ria-data.api.lynn6.top",
                    null
                ));
                needsSave = true;
            }
            
            // 更新版本号
            if (needsSave) {
                config.version = 2;
                config.save();
                System.out.println("[ToriiFind] 已添加新的默认数据源");
            }
        }
    }
    
    public void save() {
        try {
            Path configFile = getConfigPath();
            Files.createDirectories(configFile.getParent());
            
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            
            Representer representer = new Representer(options);
            // 禁用全局标签，避免类型标签出现在YAML文件中
            representer.addClassTag(SourceConfig.class, org.yaml.snakeyaml.nodes.Tag.MAP);
            representer.addClassTag(DataSource.class, org.yaml.snakeyaml.nodes.Tag.MAP);
            
            Yaml yaml = new Yaml(representer, options);
            
            try (FileWriter writer = new FileWriter(configFile.toFile())) {
                yaml.dump(this, writer);
            }
            
            System.out.println("[ToriiFind] 配置文件已保存");
        } catch (IOException e) {
            System.err.println("[ToriiFind] 保存配置文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 安全保存 - 只在确实有变化时保存
     */
    public void safeSave() {
        try {
            Path configFile = getConfigPath();
            
            // 如果文件不存在，直接保存
            if (!Files.exists(configFile)) {
                save();
                return;
            }
            
            // 读取现有文件内容并比较
            try {
                SourceConfig existing = loadOrCreateDefault();
                
                // 比较关键配置是否有变化
                boolean hasChanges = !this.currentSource.equals(existing.currentSource) ||
                                   !this.sources.equals(existing.sources) ||
                                   this.version != existing.version;
                
                if (hasChanges) {
                    save();
                } else {
                    System.out.println("[ToriiFind] 配置无变化，跳过保存");
                }
            } catch (Exception e) {
                // 比较失败，直接保存
                save();
            }
        } catch (Exception e) {
            System.err.println("[ToriiFind] 安全保存失败: " + e.getMessage());
        }
    }
    
    private static Path getConfigPath() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path toriifindDir = configDir.resolve("toriifind");
        return toriifindDir.resolve("config.yml");
    }
}