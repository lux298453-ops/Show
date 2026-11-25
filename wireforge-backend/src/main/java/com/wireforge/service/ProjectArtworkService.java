package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.ArtworkAsset;
import com.wireforge.mapper.ArtworkAssetMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional image generation after HTML validation.
 * Artwork is cached locally on disk and permanently persisted into TiDB Cloud (artwork_asset),
 * ensuring zero image loss even when cloud containers (e.g. Render) restart or sleep.
 */
@Slf4j
@Service
public class ProjectArtworkService {
    private static final String FRAMING = "data-wf-artwork-framing";
    private static final Set<String> IMAGE_TYPES = Set.of("image", "avatar", "banner", "background");
    private final ObjectMapper json = new ObjectMapper();
    private final Path root;
    private final String baseUrl, apiKey, model;
    private final boolean enabled;
    private final int timeoutSeconds, maxPerPage;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    private final ArtworkAssetMapper artworkAssetMapper;

    public record Artwork(String key, String name, String prompt, String url, String kind,
                          String sourceKey, String promptVersion, ArtworkStyle styleProfile) {
        public Artwork(String key, String name, String prompt, String url) { this(key, name, prompt, url, "character"); }
        public Artwork(String key, String name, String prompt, String url, String kind) {
            this(key, name, prompt, url, kind, key, "", null);
        }
        public Artwork {
            if (kind == null || kind.isBlank()) kind = "character";
            if (sourceKey == null || sourceKey.isBlank()) sourceKey = key;
            if (promptVersion == null) promptVersion = "";
        }
        Artwork at(String location) { return new Artwork(key, name, prompt, location, kind, sourceKey, promptVersion, styleProfile); }
        boolean reusable() { return ArtworkStyle.VERSION.equals(promptVersion) && styleProfile != null; }
    }

    /** New requests get new immutable URLs; retrying a completed image reuses its file. */
    public Artwork generateAdjustment(long projectId, long pageId, String requestId, String source,
            String instruction, String target, String kind, String name, String description, String aspectRatio) {
        return generateAdjustment(projectId, pageId, requestId, source, instruction, target, kind, name, description, aspectRatio, null);
    }

    public Artwork generateAdjustment(long projectId, long pageId, String requestId, String source,
            String instruction, String target, String kind, String name, String description, String aspectRatio, ArtworkStyle style) {
        if (!enabled || apiKey.isBlank()) {
            throw new IllegalStateException("图片生成服务尚未开启或配置，原画板已保留");
        }
        String key = "ai-adjust-" + digest(pageId + "\n" + requestId + "\n" + digest(source)
                + "\n" + instruction + "\n" + target + "\n" + kind + "\n" + model + "\n" + ArtworkStyle.VERSION);
        synchronized (locks.computeIfAbsent(projectId, id -> new Object())) {
            Artwork cached = assets(projectId).stream().filter(item -> key.equals(item.key())).findFirst().orElse(null);
            if (cached != null) return cached;
            try {
                ArtworkAspect aspect = ArtworkAspect.declared(aspectRatio);
                if (aspect == null) aspect = ArtworkAspect.PORTRAIT;
                return generate(projectId, key, name, description, kind, aspect, "", key(name), style);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("[AI 调整图片] 项目 {} 页面 {} 生成失败（{}）", projectId, pageId, e.getClass().getSimpleName());
                throw new IllegalStateException("图片生成未完成，原画板已保留；可重试本次请求，已成功的图片会复用");
            }
        }
    }

    public ProjectArtworkService(String designsDir, String baseUrl, String apiKey, String model,
            boolean enabled, int timeoutSeconds, int maxPerPage) {
        this(designsDir, baseUrl, apiKey, model, enabled, timeoutSeconds, maxPerPage, null);
    }

    @Autowired
    public ProjectArtworkService(@Value("${wireforge.designs-dir}") String designsDir,
            @Value("${wireforge.ai.artwork.base-url:${wireforge.ai.base-url}}") String baseUrl,
            @Value("${wireforge.ai.artwork.api-key:${wireforge.ai.api-key}}") String apiKey,
            @Value("${wireforge.ai.artwork.model:gpt-image-2}") String model,
            @Value("${wireforge.ai.artwork.enabled:false}") boolean enabled,
            @Value("${wireforge.ai.artwork.timeout-seconds:180}") int timeoutSeconds,
            @Value("${wireforge.ai.artwork.max-per-page:12}") int maxPerPage,
            @Autowired(required = false) ArtworkAssetMapper artworkAssetMapper) {
        this.root = Path.of(designsDir).toAbsolutePath().normalize().resolve(".wireforge-artwork");
        this.baseUrl = baseUrl.replaceAll("/+$", ""); this.apiKey = apiKey.trim(); this.model = model;
        this.enabled = enabled; this.timeoutSeconds = Math.max(10, timeoutSeconds); this.maxPerPage = Math.max(0, Math.min(12, maxPerPage));
        this.artworkAssetMapper = artworkAssetMapper;
    }

    @PostConstruct
    public void syncDiskArtworksToDatabase() {
        if (artworkAssetMapper == null || !Files.isDirectory(root)) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try (var projectDirs = Files.list(root)) {
                for (Path projDir : projectDirs.filter(Files::isDirectory).toList()) {
                    String dirName = projDir.getFileName().toString();
                    if (!dirName.matches("\\d+")) continue;
                    long projectId = Long.parseLong(dirName);
                    try (var jsonFiles = Files.list(projDir)) {
                        for (Path jsonFile : jsonFiles.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).toList()) {
                            String fileName = jsonFile.getFileName().toString();
                            String digest = fileName.substring(0, 64);
                            Path pngFile = projDir.resolve(digest + ".png");
                            if (!Files.isRegularFile(pngFile)) continue;
                            try {
                                Long count = artworkAssetMapper.selectCount(
                                        Wrappers.<ArtworkAsset>lambdaQuery()
                                                .eq(ArtworkAsset::getProjectId, projectId)
                                                .eq(ArtworkAsset::getDigest, digest));
                                if (count == null || count == 0) {
                                    String metaStr = Files.readString(jsonFile);
                                    Artwork item = json.readValue(metaStr, Artwork.class);
                                    byte[] bytes = Files.readAllBytes(pngFile);
                                    ArtworkAsset asset = ArtworkAsset.builder()
                                            .projectId(projectId)
                                            .digest(digest)
                                            .artworkKey(item.key())
                                            .name(item.name())
                                            .prompt(item.prompt())
                                            .kind(item.kind())
                                            .imageData(bytes)
                                            .jsonData(metaStr)
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    artworkAssetMapper.insert(asset);
                                    log.info("[角色素材同步] 项目 {} 素材 {} 首次同步持久化至数据库", projectId, item.key());
                                }
                            } catch (Exception ex) {
                                log.warn("[角色素材同步] 同步素材文件失败 ({}): {}", fileName, ex.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[角色素材同步] 启动自动同步扫描异常: {}", e.getMessage());
            }
        });
    }

    public List<Artwork> assets(long projectId) {
        Path folder = folder(projectId);
        Map<String, Artwork> map = new LinkedHashMap<>();
        if (Files.isDirectory(folder)) {
            try (var files = Files.list(folder)) {
                for (Path file : files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).sorted().toList()) {
                    try {
                        Artwork item = json.readValue(Files.readString(file), Artwork.class);
                        String digest = digest(item.key());
                        if (file.getFileName().toString().equals(digest + ".json") && Files.isRegularFile(folder.resolve(digest + ".png")))
                            map.put(digest, item.at(url(projectId, digest)));
                    } catch (Exception e) { log.warn("[角色素材] 忽略损坏的项目素材索引: {}", file.getFileName()); }
                }
            } catch (Exception e) { log.warn("[角色素材] 项目 {} 读取素材索引失败", projectId); }
        }

        // Supplement from TiDB database for any artwork wiped from ephemeral container disk
        // 关键优化：严禁加载 MEDIUMBLOB 二进制图片数据，仅投影轻量级元数据字段，杜绝堆内存 OOM
        if (artworkAssetMapper != null) {
            try {
                List<ArtworkAsset> dbAssets = artworkAssetMapper.selectList(
                        Wrappers.<ArtworkAsset>lambdaQuery()
                                .select(ArtworkAsset::getDigest, ArtworkAsset::getArtworkKey, ArtworkAsset::getName, ArtworkAsset::getPrompt, ArtworkAsset::getKind, ArtworkAsset::getJsonData)
                                .eq(ArtworkAsset::getProjectId, projectId));
                for (ArtworkAsset a : dbAssets) {
                    if (!map.containsKey(a.getDigest())) {
                        Artwork item = new Artwork(a.getArtworkKey(), a.getName(), a.getPrompt(), url(projectId, a.getDigest()), a.getKind());
                        if (a.getJsonData() != null && !a.getJsonData().isBlank()) {
                            try {
                                Artwork metadata = json.readValue(a.getJsonData(), Artwork.class);
                                if (Objects.equals(metadata.key(), a.getArtworkKey())) item = metadata.at(item.url());
                            } catch (Exception ignored) { /* Old metadata remains usable for existing pages. */ }
                        }
                        map.put(a.getDigest(), item);
                    }
                }
            } catch (Exception e) {
                log.warn("[角色素材] 数据库拉取项目素材失败: {}", e.getMessage());
            }
        }
        return new ArrayList<>(map.values());
    }

    public List<Artwork> reusableAssets(long projectId) { return assets(projectId).stream().filter(Artwork::reusable).toList(); }

    public String prompt(long projectId) {
        StringBuilder prompt = new StringBuilder("\n【角色图片生成】\n优先使用与原稿主体和画风匹配的原素材。缓存图片须匹配类型、主体、形态、画风与比例。\n");
        for (Artwork item : reusableAssets(projectId)) prompt.append("- key=").append(item.sourceKey()).append("；名称=").append(item.name())
                .append("；类型=").append(item.kind()).append("；URL=").append(item.url()).append("；style_profile=").append(item.styleProfile().json()).append('\n');
        prompt.append(ArtworkStyle.CONTRACT);
        if (enabled) prompt.append("""
                缺少语义匹配的图片时，在纯插画容器上声明 data-wf-artwork-key="角色名称-形态"、
                data-wf-artwork-name="角色名称与形态"、data-wf-artwork-prompt="依据原稿的主体与构图描述"，
                data-wf-artwork-profile 保存上述 style_profile JSON（正确转义HTML属性引号），data-wf-artwork-kind="character/background/illustration/product"。
                data-wf-artwork-aspect="portrait/landscape/square" 按原稿实际放图区域的宽高选取。素材对应已识别的 image、avatar、banner 或 background 时，在该图片区域使用它的 data-wf-element-id。
                角色在原稿中只露头部时标记 data-wf-artwork-framing="headshot"，描述头部特写与留边。原稿显示全身时标记 data-wf-artwork-framing="full-body"，描述完整轮廓。头像框的形状沿用原稿。
                key同一角色同一形态跨页保持一致，不包含页面名、位置、价格、等级标签；不同皮肤使用不同key。
                仅描述原稿实际存在的物种、造型、姿态、主色和构图；材质、饰品、武器与光照只在原稿确实存在时描述。
                画风按每张素材的 profile 决定，不统一精细化。服务生成不含文字、卡框和按钮的图片并持久化。
                容器给出明确高度与宽高比，备用插画遵循原稿的造型与细节密度。
                每个缺图角色都必须声明上述生图属性，禁止只画SVG而遗漏生图声明。不要把卡片标题、价格和按钮放进该容器。
                原素材直接引用真实URL。复用生成图片也必须在对应独立图片或插画容器保留 key、kind、profile，供服务端校验画风。
                只有主体、形态与profile均匹配的缓存才复用；不能因角色同名或同项目参考页已有图片而沿用错误画风。未揭晓角色保留原稿剪影，不生成外貌。
                """);
        else prompt.append("自动图片生成未开启；无匹配素材时使用与原稿画风及细节密度一致的SVG备用，不编造图片URL。\n");
        return prompt.toString();
    }

    /** Only the first generation may create images; screenshot repair reuses the cache. */
    public String resolve(long projectId, String html, Set<String> allowedAssets, boolean generate) {
        return resolve(projectId, html, allowedAssets, generate, List.of());
    }

    String resolve(long projectId, String html, Set<String> allowedAssets, boolean generate,
                   List<com.wireforge.entity.Element> sources) {
        Map<String, com.wireforge.entity.Element> imageSources = new HashMap<>();
        for (var source : sources) {
            if (source.getId() != null && source.getType() != null && IMAGE_TYPES.contains(source.getType())) {
                imageSources.put(source.getId().toString(), source);
            }
        }
        synchronized (locks.computeIfAbsent(projectId, id -> new Object())) {
            var doc = Jsoup.parse(html); doc.outputSettings().prettyPrint(false);
            Map<String, Artwork> cache = new LinkedHashMap<>(), byUrl = new HashMap<>();
            assets(projectId).forEach(a -> { cache.put(a.key(), a); byUrl.put(a.url(), a); });
            Set<String> attempted = new HashSet<>(); int created = 0; boolean changed = false;
            for (var node : doc.select("[data-wf-artwork-key]")) {
                if (!node.is("div,figure,span,img") || !node.select("button,input,a,[data-nav],[data-modal]").isEmpty()) continue;
                String sourceKey = key(node.attr("data-wf-artwork-key"));
                if (sourceKey.isBlank()) continue;
                Artwork declared = cache.get(sourceKey);
                if (declared != null) sourceKey = declared.sourceKey();
                String kind = node.attr("data-wf-artwork-kind");
                if (!Set.of("background", "character", "product", "illustration").contains(kind)) kind = "character";
                ArtworkStyle profile = ArtworkStyle.parse(node.attr(ArtworkStyle.ATTRIBUTE));
                String description = node.attr("data-wf-artwork-prompt").strip();

                // Original assets win. Repairs preserve their existing images without silently changing style.
                var images = node.is("img") ? List.of(node) : node.select("img[src]");
                var supplied = images.stream().filter(image -> allowedAssets.contains(image.attr("src")))
                        .filter(image -> image.closest("[data-wf-artwork-fallback]") == null).toList();
                if (supplied.stream().anyMatch(image -> !generate || !byUrl.containsKey(image.attr("src")))) continue;

                var source = imageSources.get(node.attr("data-wf-element-id"));
                String framing = framing(node, kind);
                ArtworkAspect aspect = source == null ? null : ArtworkAspect.fromDimensions(source.getWidth(), source.getHeight());
                if (aspect == null) aspect = ArtworkAspect.fromMarkup(node);
                if (aspect == null) aspect = ArtworkAspect.declared(node.attr(ArtworkAspect.ATTRIBUTE));
                if (aspect == null) {
                    boolean avatar = "headshot".equals(framing) || "avatar".equals(node.attr("data-wf-role"))
                            || (source != null && "avatar".equals(source.getType()));
                    aspect = avatar ? ArtworkAspect.SQUARE : ArtworkAspect.PORTRAIT;
                }

                String legacyKey = generationKey(sourceKey, kind, profile, description);
                String key = "style-" + digest(legacyKey + "\n" + aspect.size() + "\n" + framing);
                Artwork artwork = cache.get(key);
                if (artwork == null) artwork = matchingLegacyImage(projectId, cache.get(legacyKey), aspect, framing, description);
                if (artwork != null) {
                    String cachedUrl = artwork.url();
                    var rendered = supplied.stream().filter(image -> cachedUrl.equals(image.attr("src"))).findFirst().orElse(null);
                    if (rendered != null) {
                        changed |= applyFraming(rendered, framing);
                        continue;
                    }
                }
                if (artwork == null && enabled && generate && !description.isBlank() && created < maxPerPage && attempted.add(key)) {
                    created++;
                    try {
                        artwork = generate(projectId, key, node.attr("data-wf-artwork-name"), description, kind, aspect, framing, sourceKey, profile);
                        cache.put(key, artwork);
                        byUrl.put(artwork.url(), artwork);
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        // Never print provider response bodies, prompts or credentials; do not retry possibly billed calls.
                        log.warn("[角色素材] 项目 {} 的角色 {} 图片未生成，保留备用插画（{}）", projectId, key, e.getClass().getSimpleName());
                    }
                }
                if (artwork == null) continue;
                allowedAssets.add(artwork.url());
                if (node.is("img")) {
                    node.attr("src", artwork.url()).removeAttr("srcset").removeAttr("sizes");
                    applyFraming(node, framing);
                    changed = true;
                    continue;
                }
                node.attr("data-wf-artwork-resolved", "true");
                String artworkUrl = artwork.url();
                // Returning to an earlier style restores its existing node and editing identity.
                var image = node.children().stream().filter(child -> child.is("img") && artworkUrl.equals(child.attr("src")))
                        .findFirst().orElse(null);
                for (var child : node.children()) {
                    if (child != image) child.removeAttr("data-wf-artwork-image").attr("data-wf-artwork-fallback", "true");
                }
                if (image == null) {
                    image = node.appendElement("img").attr("data-wf-role", "image")
                            .attr("data-wf-uid", node.attr("data-wf-uid") + "-artwork-" + digest(artwork.key()).substring(0, 12));
                }
                image.attr("src", artworkUrl).attr("alt", artwork.name()).attr("data-wf-artwork-image", "true")
                        .removeAttr("data-wf-artwork-fallback").removeAttr("srcset");
                if (profile != null) image.attr(ArtworkStyle.ATTRIBUTE, profile.json());
                else image.removeAttr(ArtworkStyle.ATTRIBUTE);
                applyFraming(image, framing);
                changed = true;
            }
            // A visual repair may keep the resolved image but omit the service's CSS.
            // Reapply these rules even when no image needs to be generated or inserted.
            boolean managedImages = doc.select("[data-wf-artwork-resolved=true]>img[data-wf-artwork-image],img[data-wf-artwork-image][" + FRAMING + "]")
                    .stream().anyMatch(n -> allowedAssets.contains(n.attr("src")));
            if (!changed && !managedImages) return html;
            doc.select("style[data-wf-artwork-style]").remove();
            doc.head().appendElement("style").attr("data-wf-artwork-style", "true").appendChild(new DataNode("""
                    [data-wf-artwork-resolved=true]{position:relative!important;overflow:hidden}
                    [data-wf-artwork-resolved=true]>[data-wf-artwork-fallback]{display:none!important}
                    [data-wf-artwork-resolved=true]>img[data-wf-artwork-image]{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;object-fit:var(--wf-artwork-fit,cover)!important;object-position:var(--wf-artwork-position,center top)!important;transform:none!important;display:block}
                    img[data-wf-artwork-image][data-wf-artwork-framing=full-body]:not([data-wf-artwork-layout]){object-fit:contain!important;object-position:center!important}
                    img[data-wf-artwork-image][data-wf-artwork-framing=headshot]:not([data-wf-artwork-layout]){object-fit:cover!important;object-position:center!important}
                    """));
            return doc.outerHtml();
        }
    }

    private static String framing(org.jsoup.nodes.Element node, String kind) {
        if (!"character".equals(kind)) return "";
        String declared = node.attr(FRAMING);
        return "headshot".equals(declared) || "full-body".equals(declared) ? declared : "";
    }

    private static boolean applyFraming(org.jsoup.nodes.Element image, String framing) {
        if (framing.isEmpty() || (framing.equals(image.attr(FRAMING)) && image.hasAttr("data-wf-artwork-image"))) return false;
        image.attr(FRAMING, framing).attr("data-wf-artwork-image", "true");
        return true;
    }

    private Artwork matchingLegacyImage(long projectId, Artwork legacy, ArtworkAspect aspect, String framing, String description) {
        if (legacy == null) return null;
        // Legacy keys did not record framing. For an explicit crop, require the same original composition description.
        if (!framing.isEmpty() && (description.isBlank() || !description.equals(legacy.prompt()))) return null;
        try (var png = new DataInputStream(image(projectId, digest(legacy.key())).getInputStream())) {
            // Read the PNG signature and dimensions from IHDR without decoding the bitmap.
            if (png.readLong() != 0x89504e470d0a1a0aL || png.readInt() != 13 || png.readInt() != 0x49484452) return null;
            return ArtworkAspect.fromDimensions((double) png.readInt(), (double) png.readInt()) == aspect ? legacy : null;
        } catch (IOException e) {
            return null;
        }
    }

    private String generationKey(String sourceKey, String kind, ArtworkStyle profile, String description) {
        // Legacy unknown profiles cannot match a new known style; URLs remain immutable across policy changes.
        String style = profile == null ? "unrecorded:" + description.strip().replaceAll("\\s+", " ") : profile.identity();
        return "style-" + digest(ArtworkStyle.VERSION + "\n" + model + "\n" + kind + "\n" + sourceKey + "\n" + style);
    }

    private Artwork generate(long projectId, String key, String name, String description, String kind, ArtworkAspect aspect,
                             String framing, String sourceKey, ArtworkStyle profile) throws Exception {
        if (apiKey.isBlank()) throw new IllegalStateException("图片生成未配置Key");
        String subjectPrompt = switch (kind) {
            case "background" -> "Create ONE background illustration matching the described reference. Keep the requested space for overlaid interface content. Do not add UI panels or unrelated characters. ";
            case "product" -> "Create ONE product image matching the described object, reference style and composition. ";
            case "illustration" -> "Create ONE illustration matching the requested subject, reference style, composition and palette. ";
            default -> "Create ONE character or pet image matching the described reference and species. "
                + "Follow the requested framing: a full-body companion must retain its full silhouette; card portraits fill the frame. Important features clear at thumbnail size. "
                + "Preserve the requested backdrop, including plain color or empty space when that is the reference. ";
        };
        String composition = switch (framing) {
            case "headshot" -> "Frame only the centered head. Leave enough margin to keep hair, ears and headwear inside a circular avatar. ";
            case "full-body" -> "Keep the entire character within the image, including the feet and any visible extremities. ";
            default -> "";
        };
        String artPrompt = subjectPrompt + ArtworkStyle.POLICY + (profile == null ? "" : profile.prompt()) + composition
                + "No text, letters, numbers, UI, card frame, watermark or logos. Subject: "
                + description.substring(0, Math.min(4000, description.length()));
        Map<String, Object> body = Map.of("model", model, "prompt", artPrompt, "n", 1, "size", aspect.size(), "quality", "medium", "output_format", "png");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/images/generations"))
                .timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_1_1).build().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("图片接口HTTP " + response.statusCode());
        String base64 = json.readTree(response.body()).path("data").path(0).path("b64_json").asText();
        if (base64.isBlank() || base64.length() > 24_000_000) throw new IllegalStateException("图片接口没有有效base64图片");
        byte[] bytes = Base64.getDecoder().decode(base64);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() < 256 || image.getHeight() < 256) throw new IllegalStateException("图片接口返回无效图片");
        Path folder = folder(projectId); Files.createDirectories(folder);
        String digest = digest(key);
        // Persist real PNG bytes, not a short-lived provider URL.
        Path temporary = Files.createTempFile(folder, digest, ".tmp");
        try {
            ImageIO.write(image, "png", temporary.toFile());
            Files.move(temporary, folder.resolve(digest + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        Artwork artwork = new Artwork(key, name.isBlank() ? sourceKey : name, description, url(projectId, digest), kind,
                sourceKey, ArtworkStyle.VERSION, profile);
        String metadataStr = json.writeValueAsString(artwork);
        Path metadata = Files.createTempFile(folder, digest, ".tmp");
        try {
            Files.writeString(metadata, metadataStr);
            Files.move(metadata, folder.resolve(digest + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(metadata); }

        // Persist to TiDB database as permanent master storage
        persistToDatabase(projectId, digest, key, artwork.name(), description, kind, bytes, metadataStr);
        log.info("[角色素材] 项目 {} 保存角色 {}，已持久化至本地与数据库", projectId, key);
        return artwork;
    }

    private void persistToDatabase(long projectId, String digest, String artworkKey, String name, String prompt, String kind, byte[] bytes, String metadataStr) {
        if (artworkAssetMapper == null) return;
        try {
            ArtworkAsset existing = artworkAssetMapper.selectOne(
                    Wrappers.<ArtworkAsset>lambdaQuery()
                            .eq(ArtworkAsset::getProjectId, projectId)
                            .eq(ArtworkAsset::getDigest, digest));
            if (existing == null) {
                ArtworkAsset asset = ArtworkAsset.builder()
                        .projectId(projectId)
                        .digest(digest)
                        .artworkKey(artworkKey)
                        .name(name)
                        .prompt(prompt)
                        .kind(kind)
                        .imageData(bytes)
                        .jsonData(metadataStr)
                        .createdAt(LocalDateTime.now())
                        .build();
                artworkAssetMapper.insert(asset);
            } else {
                existing.setImageData(bytes);
                existing.setJsonData(metadataStr);
                existing.setName(name);
                existing.setPrompt(prompt);
                existing.setKind(kind);
                artworkAssetMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("[角色素材] 写入数据库持久化失败 (项目 {}, digest {}): {}", projectId, digest, e.getMessage());
        }
    }

    public Resource image(long projectId, String digest) {
        if (!digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("无效素材标识");
        Path localPng = folder(projectId).resolve(digest + ".png");
        if (Files.isRegularFile(localPng)) {
            return new FileSystemResource(localPng);
        }
        // Fallback to TiDB database if Render container local disk was wiped
        if (artworkAssetMapper != null) {
            try {
                ArtworkAsset asset = artworkAssetMapper.selectOne(
                        Wrappers.<ArtworkAsset>lambdaQuery()
                                .eq(ArtworkAsset::getProjectId, projectId)
                                .eq(ArtworkAsset::getDigest, digest));
                if (asset != null && asset.getImageData() != null && asset.getImageData().length > 0) {
                    try {
                        Path folder = folder(projectId);
                        if (!Files.isDirectory(folder)) Files.createDirectories(folder);
                        Files.write(localPng, asset.getImageData());
                        if (asset.getJsonData() != null && !asset.getJsonData().isBlank()) {
                            Files.writeString(folder.resolve(digest + ".json"), asset.getJsonData());
                        }
                    } catch (Exception ex) {
                        log.warn("[角色素材] 缓存回写磁盘失败: {}", ex.getMessage());
                    }
                    return new ByteArrayResource(asset.getImageData());
                }
            } catch (Exception e) {
                log.warn("[角色素材] 数据库读取图片失败 (项目 {}, digest {}): {}", projectId, digest, e.getMessage());
            }
        }
        return new FileSystemResource(localPng);
    }

    private Path folder(long projectId) { if (projectId <= 0) throw new IllegalArgumentException("无效项目"); return root.resolve(Long.toString(projectId)); }
    private static String url(long projectId, String digest) { return "/api/assets/artwork/" + projectId + "/" + digest + ".png"; }
    private static String key(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}]+", "-").replaceAll("^-|-$", "");
        return normalized.substring(0, Math.min(120, normalized.length()));
    }
    private static String digest(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
