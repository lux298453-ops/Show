package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 素材库服务：启动时扫描 classpath:assets/wireframe/ 下的 assets.json，构建素材映射。
 *
 * 素材 ID 约定（与 AI 提示词、前端渲染保持一致）：
 * - avatars   → avatar-01 ~ avatar-05   （文件 avatar-01.svg，无前缀）
 * - products  → product-01 ~ product-04 （文件 product-01.svg，无前缀）
 * - backgrounds → bg-01 ~ bg-03        （文件 bg-01.svg，无前缀）
 * - icons     → icon-<name>            （文件 <name>.svg，加 "icon-" 前缀）
 * - effects   → effect-<name>          （文件 <name>.svg，加 "effect-" 前缀）
 */
@Slf4j
@Service
public class AssetService {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String assetBasePath;
    /** 设计稿根目录：其下 assets/ 子目录中的文件视为用户上传素材 */
    private final String designsDir;

    /** assetId → 素材元信息 */
    private final Map<String, AssetMeta> assets = new LinkedHashMap<>();
    /** 分类目录名（avatars/products/...） → 该分类下的 assetId 列表（有序） */
    private final Map<String, List<String>> categoryAssetIds = new LinkedHashMap<>();
    /** 分类目录名 → 描述 */
    private final Map<String, String> categoryDescriptions = new LinkedHashMap<>();

    public AssetService(ResourceLoader resourceLoader,
                        ObjectMapper objectMapper,
                        @Value("${wireforge.assets.path:classpath:assets/wireframe}") String assetBasePath,
                        @Value("${wireforge.designs-dir}") String designsDir) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.assetBasePath = assetBasePath.trim();
        this.designsDir = designsDir == null ? "" : designsDir.trim();
    }

    @PostConstruct
    public void init() {
        Resource jsonRes = resourceLoader.getResource(assetBasePath + "/assets.json");
        if (!jsonRes.exists()) {
            log.warn("[素材库] 未找到 assets.json: {}，素材接口将不可用", assetBasePath + "/assets.json");
            return;
        }
        try (InputStream in = jsonRes.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            root.fields().forEachRemaining(entry -> {
                String category = entry.getKey();
                JsonNode catNode = entry.getValue();
                String description = catNode.path("description").asText("");
                List<String> ids = new ArrayList<>();
                JsonNode files = catNode.path("files");
                if (files.isArray()) {
                    for (JsonNode f : files) {
                        String fileName = f.asText();
                        if (!StringUtils.hasText(fileName)) continue;
                        String base = fileName.endsWith(".svg")
                                ? fileName.substring(0, fileName.length() - 4)
                                : fileName;
                        String prefix = "";
                        if ("icons".equals(category)) prefix = "icon-";
                        else if ("effects".equals(category)) prefix = "effect-";
                        String assetId = prefix + base;
                        assets.put(assetId, new AssetMeta(assetId, category, fileName, description));
                        ids.add(assetId);
                    }
                }
                categoryDescriptions.put(category, description);
                categoryAssetIds.put(category, ids);
            });
            log.info("[素材库] 已加载 {} 个素材，分类: {}", assets.size(), categoryAssetIds.keySet());
        } catch (IOException e) {
            log.error("[素材库] 解析 assets.json 失败: {}", e.getMessage(), e);
        }
    }

    /** 素材元信息 */
    public record AssetMeta(String assetId, String category, String fileName, String description) {
    }

    // ===== 对外接口 =====

    /** 返回素材在 classpath 下的资源定位（如 classpath:assets/wireframe/icons/home.svg），不存在返回 null */
    public String getAssetPath(String assetId) {
        AssetMeta meta = assets.get(assetId);
        if (meta == null) return null;
        String location = assetBasePath + "/" + meta.category() + "/" + meta.fileName();
        Resource r = resourceLoader.getResource(location);
        if (r.exists()) {
            try {
                return r.getFile().getAbsolutePath();
            } catch (IOException | IllegalStateException ignored) {
                try {
                    return r.getURL().toString();
                } catch (IOException ignored2) {
                    return location;
                }
            }
        }
        return location;
    }

    /** 返回素材的对外访问 URL（/api/assets/{assetId}），不存在返回空串 */
    public String getAssetUrl(String assetId) {
        return assets.containsKey(assetId) ? "/api/assets/" + assetId : "";
    }

    /**
     * 随机返回某类素材的 assetId。
     * category 既可以是前端元素类型（avatar/image/icon/background/effect），
     * 也可以是素材分类目录名（avatars/products/icons/backgrounds/effects）。
     * 无匹配时返回 null。
     */
    public String getRandomAsset(String category) {
        if (!StringUtils.hasText(category)) return null;
        String folder = mapTypeToCategory(category);
        List<String> ids = categoryAssetIds.get(folder);
        if (ids == null || ids.isEmpty()) return null;
        return ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
    }

    /** 返回全部素材列表（用于 /api/assets/list） */
    public List<Map<String, Object>> getAssetList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AssetMeta meta : assets.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assetId", meta.assetId());
            m.put("category", meta.category());
            m.put("fileName", meta.fileName());
            m.put("description", meta.description());
            m.put("url", getAssetUrl(meta.assetId()));
            list.add(m);
        }
        return list;
    }

    /** 返回素材分类（用于 /api/assets/categories） */
    public List<Map<String, Object>> getAssetCategories() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : categoryAssetIds.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", entry.getKey());
            m.put("description", categoryDescriptions.get(entry.getKey()));
            m.put("count", entry.getValue().size());
            list.add(m);
        }
        return list;
    }

    /** 取素材元信息（不存在返回 null） */
    public AssetMeta getAssetMeta(String assetId) {
        return assets.get(assetId);
    }

    /** 取素材的 Spring Resource（用于 Controller 输出二进制流），不存在返回 null */
    public Resource getAssetResource(String assetId) {
        // 用户上传素材：从文件系统读取
        Path userPath = findUserAssetPath(assetId);
        if (userPath != null && Files.exists(userPath)) {
            return new org.springframework.core.io.FileSystemResource(userPath);
        }
        AssetMeta meta = assets.get(assetId);
        if (meta == null) return null;
        return resourceLoader.getResource(assetBasePath + "/" + meta.category() + "/" + meta.fileName());
    }

    /** 是否存在该素材 */
    public boolean exists(String assetId) {
        return assets.containsKey(assetId) || findUserAssetPath(assetId) != null;
    }

    // ===== 用户上传素材（designs-dir/assets/ 子目录，动态扫描，无需重启） =====

    /** 用户素材记录：id / 展示名 / 文件绝对路径 / 所属分组（全局 或 设计稿分组名） */
    public record UserAsset(String assetId, String name, Path path, String group) {}

    private static final List<String> USER_ASSET_EXTS = List.of(".png", ".jpg", ".jpeg", ".webp", ".svg", ".gif");

    /**
     * 扫描指定素材根目录下的用户素材（含子目录，目录名并入 id 防止重名）。
     */
    private List<UserAsset> scanUserAssetsFrom(Path assetsRoot, String idPrefix, String groupLabel) {
        List<UserAsset> result = new ArrayList<>();
        if (assetsRoot == null || !Files.isDirectory(assetsRoot)) return result;
        try (var walk = Files.walk(assetsRoot, 3)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return USER_ASSET_EXTS.stream().anyMatch(n::endsWith);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String base = fileName.substring(0, fileName.lastIndexOf('.'));
                        var relParent = assetsRoot.relativize(p.getParent() == null ? assetsRoot : p.getParent());
                        String sub = relParent.getNameCount() > 0
                                ? relParent.toString().replace('\\', '-').replace('/', '-')
                                : "";
                        String slug = (sub.isEmpty() ? "" : sub + "-") + base;
                        slug = slug.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", "-");
                        if (slug.startsWith("-")) slug = slug.substring(1);
                        result.add(new UserAsset(idPrefix + slug, base, p.toAbsolutePath().normalize(), groupLabel));
                    });
        } catch (IOException e) {
            log.warn("[素材库] 扫描用户素材失败: {}", e.getMessage());
        }
        return result;
    }

    /** 全局素材（designs/assets/**）：所有页面可用 */
    public List<UserAsset> scanUserAssets() {
        return scanUserAssetsFor(null);
    }

    /**
     * 某页面可用的用户素材 = 全局素材（designs/assets/**）
     * + 本组专属素材（设计稿所在目录下的 assets/**，仅该组页面可用）。
     *
     * @param pageImagePath 页面设计稿图片的绝对路径（null = 仅全局）
     */
    public List<UserAsset> scanUserAssetsFor(Path pageImagePath) {
        List<UserAsset> out = new ArrayList<>();
        if (designsDir.isBlank()) return out;
        Path root = Paths.get(designsDir);
        out.addAll(scanUserAssetsFrom(root.resolve("assets"), "u-", "全局"));
        if (pageImagePath != null) {
            Path parent = pageImagePath.toAbsolutePath().normalize().getParent();
            if (parent != null && !parent.equals(root) && parent.startsWith(root)) {
                String groupName = parent.getFileName() == null ? "" : parent.getFileName().toString();
                out.addAll(scanUserAssetsFrom(parent.resolve("assets"),
                        "u-" + groupName.replaceAll("[^\\p{L}\\p{N}]+", "-").toLowerCase() + "-",
                        groupName));
            }
        }
        return out;
    }

    /** 按 id 查找用户素材文件路径（在全局和所有分组的 assets 目录中查找；不存在返回 null） */
    public Path findUserAssetPath(String assetId) {
        if (assetId == null || !assetId.startsWith("u-")) return null;
        if (designsDir.isBlank()) return null;
        Path root = Paths.get(designsDir);
        // 全局
        Path hit = searchAssetIn(root.resolve("assets"), assetId, "u-", "");
        if (hit != null) return hit;
        // 各分组目录
        if (Files.isDirectory(root)) {
            try (var dirs = Files.list(root)) {
                for (Path sub : dirs.filter(Files::isDirectory).toList()) {
                    if ("assets".equalsIgnoreCase(sub.getFileName().toString())) continue;
                    String groupName = sub.getFileName().toString();
                    String prefix = "u-" + groupName.replaceAll("[^\\p{L}\\p{N}]+", "-").toLowerCase() + "-";
                    hit = searchAssetIn(sub.resolve("assets"), assetId, prefix, groupName);
                    if (hit != null) return hit;
                }
            } catch (IOException e) {
                log.warn("[素材库] 遍历分组素材目录失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /** 在单个素材根目录中按 id 前缀匹配查找 */
    private Path searchAssetIn(Path assetsRoot, String assetId, String idPrefix, String groupLabel) {
        if (!Files.isDirectory(assetsRoot)) return null;
        for (UserAsset a : scanUserAssetsFrom(assetsRoot, idPrefix, groupLabel)) {
            if (a.assetId().equals(assetId)) return a.path();
        }
        return null;
    }

    /**
     * 生成注入到整页 HTML 生成提示词中的【可用素材】说明（按页面过滤：全局 + 本组素材）：
     * 按分组列出（分组名即语义），并附使用纪律。
     */
    public String getUserAssetsHtmlPrompt(Path pageImagePath) {
        List<UserAsset> userAssets = scanUserAssetsFor(pageImagePath);
        if (userAssets.isEmpty()) return "";
        Map<String, List<UserAsset>> groups = new LinkedHashMap<>();
        for (UserAsset a : userAssets) {
            groups.computeIfAbsent(a.group(), k -> new ArrayList<>()).add(a);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本页可用的本地素材图片清单（按用途/归属分组；已部署在本服务下，可直接 <img src=\"...\"> 引用）：\n");
        for (var entry : groups.entrySet()) {
            sb.append("【").append(entry.getKey()).append("】组 —— 只应在本页与该组语义一致的位置使用：\n");
            for (UserAsset a : entry.getValue()) {
                sb.append("- <img src=\"/api/assets/").append(a.assetId()).append("\"> （名称：")
                        .append(a.name()).append("）\n");
            }
        }
        sb.append("""
                素材使用纪律：
                1. 对号入座——只在设计稿中真实出现该图形且语义匹配的位置使用，禁止当装饰随意铺放；
                2. 状态栏类素材只能用于页面最顶部的状态栏区域；
                3. 每个素材同页面最多出现一次（设计稿明确重复除外）；
                4. 无法对应的元素一律用内联 SVG/CSS 绘制，宁可不用素材也不强行套用；
                5. 分组标注了归属（全局 / 某设计稿组）的素材，只能用在对应内容的页面上。
                """);
        return sb.toString();
    }

    /**
     * 生成注入到 AI 系统提示词中的【素材库】说明段，严格依据 assets.json，避免臆造 ID。
     *
     * @param pageImagePath 页面设计稿图片路径（用于筛选该页面可用的用户分组素材；null = 仅内置+全局素材）
     */
    public String getAssetLibraryPrompt(Path pageImagePath) {
        if (assets.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("【素材库】\n");
        sb.append("项目预置了以下素材库（中性灰调占位图，用于表现设计稿中的人物头像、产品图、图标、背景与特效，类似设计工具的中保真原型）。\n");
        sb.append("当你识别到对应元素时，必须在 JSON 中输出 asset_id 字段，取值必须严格来自下列 ID 列表；不要臆造列表中不存在的 ID；无法对应素材库的元素可省略该字段。\n\n");

        sb.append("- avatars（人物头像）：").append(rangeList(categoryAssetIds.get("avatars"))).append('\n');
        sb.append("- products（产品/商品图片）：").append(rangeList(categoryAssetIds.get("products"))).append('\n');
        sb.append("- icons（功能图标）：").append(join(categoryAssetIds.get("icons"))).append('\n');
        sb.append("- backgrounds（背景图）：").append(rangeList(categoryAssetIds.get("backgrounds"))).append('\n');
        sb.append("- effects（特效/光晕/渐变）：").append(join(categoryAssetIds.get("effects"))).append('\n');

        sb.append("\n元素类型与素材映射规则（必须按此填写 asset_id）：\n");
        sb.append("- 头像/用户头像 → type: \"avatar\", asset_id: \"avatar-XX\"\n");
        sb.append("- 产品/商品图片 → type: \"image\", asset_id: \"product-XX\"\n");
        sb.append("- 功能图标/图标按钮 → type: \"icon\", asset_id: \"icon-XX\"\n");
        sb.append("- 背景图 → type: \"background\", asset_id: \"bg-XX\"\n");
        sb.append("- 特效/光晕/渐变 → type: \"effect\", asset_id: \"effect-XX\"\n");

        // 用户上传素材：全局素材所有页面可用；组专属素材只提供给该组内的设计稿
        List<UserAsset> userAssets = scanUserAssetsFor(pageImagePath);
        if (!userAssets.isEmpty()) {
            Map<String, List<UserAsset>> groups = new LinkedHashMap<>();
            for (UserAsset a : userAssets) {
                groups.computeIfAbsent(a.group(), k -> new ArrayList<>()).add(a);
            }
            sb.append("\n【用户上传素材】（项目专属素材，优先级高于上述内置素材；但必须对号入座使用）：\n");
            for (var entry : groups.entrySet()) {
                sb.append("- 分组【").append(entry.getKey()).append("】：");
                var ids = entry.getValue().stream().map(UserAsset::assetId).toList();
                sb.append(String.join(", ", ids)).append('\n');
            }
            sb.append("""
                    用户素材使用纪律：
                    1. 对号入座——只在元素语义与素材名称/分组一致时填写对应 asset_id，禁止当装饰乱配；
                    2. 状态栏类素材只用于状态栏元素；
                    3. 每个素材在页面中最多引用一次（设计稿明确重复除外）；
                    4. 无法对应的元素不填 asset_id，宁可省略也不强行套用；
                    5. 分组标注了归属（全局 / 某设计稿组）的素材只能用于对应内容的元素。
                    """);
        }

        sb.append("\nJSON 中每个元素可包含可选字段 \"asset_id\"（字符串）；识别到上述类型之一时务必填写，无法对应素材库的元素省略该字段。\n");
        return sb.toString();
    }

    // ===== 内部工具 =====

    private static String mapTypeToCategory(String type) {
        return switch (type) {
            case "avatar" -> "avatars";
            case "image" -> "products";
            case "icon" -> "icons";
            case "background" -> "backgrounds";
            case "effect" -> "effects";
            default -> type; // 直接当作分类目录名
        };
    }

    /** 连续编号（如 avatar-01..avatar-05）压缩为 "avatar-01 ~ avatar-05"，否则原样罗列 */
    private static String rangeList(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "（空）";
        if (ids.size() == 1) return ids.get(0);
        return ids.get(0) + " ~ " + ids.get(ids.size() - 1);
    }

    private static String join(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "（空）";
        return String.join(", ", ids);
    }
}
