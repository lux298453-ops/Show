package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional image generation after HTML validation. Artwork is cached per project, outside page transactions. */
@Slf4j
@Service
public class ProjectArtworkService {
    private final ObjectMapper json = new ObjectMapper();
    private final Path root;
    private final String baseUrl, apiKey, model;
    private final boolean enabled;
    private final int timeoutSeconds, maxPerPage;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public record Artwork(String key, String name, String prompt, String url, String kind) {
        public Artwork(String key, String name, String prompt, String url) { this(key, name, prompt, url, "character"); }
        public Artwork { if (kind == null || kind.isBlank()) kind = "character"; }
    }

    /** New requests get new immutable URLs; retrying a completed image reuses its file. */
    public Artwork generateAdjustment(long projectId, long pageId, String requestId, String source,
            String instruction, String target, String kind, String name, String description, String aspectRatio) {
        if (!enabled || apiKey.isBlank()) {
            throw new IllegalStateException("图片生成服务尚未开启或配置，原画板已保留");
        }
        String key = "ai-adjust-" + digest(pageId + "\n" + requestId + "\n" + digest(source)
                + "\n" + instruction + "\n" + target + "\n" + kind + "\n" + model);
        synchronized (locks.computeIfAbsent(projectId, id -> new Object())) {
            Artwork cached = assets(projectId).stream().filter(item -> key.equals(item.key())).findFirst().orElse(null);
            if (cached != null) return cached;
            try {
                String size = switch (aspectRatio) {
                    case "landscape" -> "1536x1024";
                    case "square" -> "1024x1024";
                    default -> "1024x1536";
                };
                return generate(projectId, key, name, description, kind, size);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("[AI 调整图片] 项目 {} 页面 {} 生成失败（{}）", projectId, pageId, e.getClass().getSimpleName());
                throw new IllegalStateException("图片生成未完成，原画板已保留；可重试本次请求，已成功的图片会复用");
            }
        }
    }

    public ProjectArtworkService(@Value("${wireforge.designs-dir}") String designsDir,
            @Value("${wireforge.ai.artwork.base-url:${wireforge.ai.base-url}}") String baseUrl,
            @Value("${wireforge.ai.artwork.api-key:${wireforge.ai.api-key}}") String apiKey,
            @Value("${wireforge.ai.artwork.model:gpt-image-2}") String model,
            @Value("${wireforge.ai.artwork.enabled:false}") boolean enabled,
            @Value("${wireforge.ai.artwork.timeout-seconds:180}") int timeoutSeconds,
            @Value("${wireforge.ai.artwork.max-per-page:12}") int maxPerPage) {
        this.root = Path.of(designsDir).toAbsolutePath().normalize().resolve(".wireforge-artwork");
        this.baseUrl = baseUrl.replaceAll("/+$", ""); this.apiKey = apiKey.trim(); this.model = model;
        this.enabled = enabled; this.timeoutSeconds = Math.max(10, timeoutSeconds); this.maxPerPage = Math.max(0, Math.min(12, maxPerPage));
    }

    public List<Artwork> assets(long projectId) {
        Path folder = folder(projectId);
        if (!Files.isDirectory(folder)) return List.of();
        try (var files = Files.list(folder)) {
            List<Artwork> result = new ArrayList<>();
            for (Path file : files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).sorted().toList()) {
                try {
                    Artwork item = json.readValue(Files.readString(file), Artwork.class);
                    String digest = digest(item.key());
                    if (file.getFileName().toString().equals(digest + ".json") && Files.isRegularFile(folder.resolve(digest + ".png")))
                        result.add(new Artwork(item.key(), item.name(), item.prompt(), url(projectId, digest), item.kind()));
                } catch (Exception e) { log.warn("[角色素材] 忽略损坏的项目素材索引: {}", file.getFileName()); }
            }
            return result;
        } catch (Exception e) { log.warn("[角色素材] 项目 {} 读取素材索引失败", projectId); return List.of(); }
    }

    public String prompt(long projectId) {
        StringBuilder prompt = new StringBuilder("\n【角色图片生成】\n优先使用可用素材中的原角色图片。以下是本项目已保存的图片；按名称及类型匹配，背景不能当作角色。相同角色及形态复用key和URL：\n");
        for (Artwork item : assets(projectId)) prompt.append("- key=").append(item.key()).append("；名称=").append(item.name()).append("；类型=").append(item.kind()).append("；URL=").append(item.url()).append('\n');
        if (enabled) prompt.append("""
                缺少语义匹配的图片时，在纯插画容器上声明 data-wf-artwork-key="角色名称-形态"、
                data-wf-artwork-name="角色名称与形态"、data-wf-artwork-prompt="详细中文或英文美术描述"。
                key同一角色同一形态跨页保持一致，不包含页面名、位置、价格、等级标签；不同皮肤使用不同key。
                描述必须根据本页设计稿写明性别/物种、发型发色、脸部、服装材质、饰品、武器、姿态、主色和构图，
                用精细游戏角色插画而非几何人形。服务会生成不含文字、卡框和按钮的图片并持久化。
                容器给出明确高度与宽高比，内部备用只用轻量轮廓或角色名牌，不编写大量人物SVG路径；角色精细度由图片提供。
                每个缺图角色都必须声明上述生图属性，禁止只画SVG而遗漏生图声明。不要把卡片标题、价格和按钮放进该容器。
                已有原角色图片或缓存图片时直接引用真实URL，禁止重复请求生图。未揭晓角色保留原稿剪影，不生成外貌。
                """);
        else prompt.append("自动图片生成未开启；无匹配素材时使用有角色辨识度的精细SVG备用，不编造图片URL。\n");
        return prompt.toString();
    }

    /** Only the first generation may create images; screenshot repair reuses the cache. */
    public String resolve(long projectId, String html, Set<String> allowedAssets, boolean generate) {
        synchronized (locks.computeIfAbsent(projectId, id -> new Object())) {
            var doc = Jsoup.parse(html); doc.outputSettings().prettyPrint(false);
            Map<String, Artwork> cache = new LinkedHashMap<>(); assets(projectId).forEach(a -> cache.put(a.key(), a));
            Set<String> attempted = new HashSet<>(); int created = 0; boolean changed = false;
            for (var node : doc.select("[data-wf-artwork-key]")) {
                // A supplied original image always wins over a new generated illustration.
                if (node.is("img") && allowedAssets.contains(node.attr("src"))) continue;
                if (node.select("img[src]").stream().anyMatch(n -> allowedAssets.contains(n.attr("src")))) continue;
                if (!node.is("div,figure,span") || !node.select("button,input,a,[data-nav],[data-modal]").isEmpty()) continue;
                String key = key(node.attr("data-wf-artwork-key"));
                if (key.isBlank()) continue;
                Artwork artwork = cache.get(key);
                String description = node.attr("data-wf-artwork-prompt").strip();
                if (artwork == null && enabled && generate && !description.isBlank() && created < maxPerPage && attempted.add(key)) {
                    created++;
                    try {
                        artwork = generate(projectId, key, node.attr("data-wf-artwork-name"), description);
                        cache.put(key, artwork);
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        // Never print provider response bodies, prompts or credentials; do not retry possibly billed calls.
                        log.warn("[角色素材] 项目 {} 的角色 {} 图片未生成，保留备用插画（{}）", projectId, key, e.getClass().getSimpleName());
                    }
                }
                if (artwork == null) continue;
                allowedAssets.add(artwork.url());
                node.attr("data-wf-artwork-resolved", "true");
                for (var child : node.children()) child.attr("data-wf-artwork-fallback", "true");
                String uid = node.attr("data-wf-uid");
                node.appendElement("img").attr("src", artwork.url()).attr("alt", artwork.name())
                        .attr("data-wf-artwork-image", "true").attr("data-wf-role", "image")
                        .attr("data-wf-uid", uid + "-artwork");
                changed = true;
            }
            // A visual repair may keep the resolved image but omit the service's CSS.
            // Reapply these rules even when no image needs to be generated or inserted.
            boolean managedImages = doc.select("[data-wf-artwork-resolved=true]>img[data-wf-artwork-image]")
                    .stream().anyMatch(n -> allowedAssets.contains(n.attr("src")));
            if (!changed && !managedImages) return html;
            doc.select("style[data-wf-artwork-style]").remove();
            doc.head().appendElement("style").attr("data-wf-artwork-style", "true").appendChild(new DataNode("""
                    [data-wf-artwork-resolved=true]{position:relative!important;overflow:hidden}
                    [data-wf-artwork-resolved=true]>[data-wf-artwork-fallback]{display:none!important}
                    [data-wf-artwork-resolved=true]>img[data-wf-artwork-image]{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;object-fit:var(--wf-artwork-fit,cover)!important;object-position:var(--wf-artwork-position,center top)!important;transform:none!important;display:block}
                    """));
            return doc.outerHtml();
        }
    }

    private Artwork generate(long projectId, String key, String name, String description) throws Exception {
        return generate(projectId, key, name, description, "character", "1024x1536");
    }

    private Artwork generate(long projectId, String key, String name, String description, String kind, String size) throws Exception {
        if (apiKey.isBlank()) throw new IllegalStateException("图片生成未配置Key");
        String subjectPrompt = switch (kind) {
            case "background" -> "Create ONE polished background illustration for the described app or game. Follow its visual style and composition. Keep space for overlaid interface content. Do not add UI panels or unrelated characters. ";
            case "product" -> "Create ONE polished product illustration. Follow the described object, materials, composition and visual style. ";
            case "illustration" -> "Create ONE detailed illustration following the requested subject, composition, palette and visual style. ";
            default -> "Create ONE polished game character or pet illustration for a mobile collectible game. "
                + "Follow the specified subject, species and artistic style. Detailed face, hair or fur, costume and materials, tasteful lighting. "
                + "Follow the requested framing: a full-body companion must retain its full silhouette; card portraits fill the frame. Important features clear at thumbnail size. "
                + "Simple coordinated atmospheric backdrop. Do not use flat geometric people, emoji or generic avatar icons. ";
        };
        String artPrompt = subjectPrompt + "No text, letters, numbers, UI, card frame, watermark or logos. Subject: "
                + description.substring(0, Math.min(4000, description.length()));
        Map<String, Object> body = Map.of("model", model, "prompt", artPrompt, "n", 1, "size", size, "quality", "medium", "output_format", "png");
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
        Artwork artwork = new Artwork(key, name.isBlank() ? key : name, description, url(projectId, digest), kind);
        Path metadata = Files.createTempFile(folder, digest, ".tmp");
        try {
            Files.writeString(metadata, json.writeValueAsString(artwork));
            Files.move(metadata, folder.resolve(digest + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(metadata); }
        log.info("[角色素材] 项目 {} 保存角色 {}，同项目后续页面复用", projectId, key);
        return artwork;
    }

    public Resource image(long projectId, String digest) {
        if (!digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("无效素材标识");
        return new FileSystemResource(folder(projectId).resolve(digest + ".png"));
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
