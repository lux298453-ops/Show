package com.wireforge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * 中转站 OpenAI 兼容客户端（chat-completions 为主，responses 兜底）。
 * 兼容 AiDocumentPlatform 使用的同一中转站（api.flintic.uk）的两种协议。
 */
@Slf4j
@Component
public class AiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    /** 整页 HTML 生成专用模型（空=用默认 model）；HTML 直出对模型代码能力要求更高，可单独配更强模型 */
    private final String htmlModel;
    private final int maxOutputTokens;
    private final int timeoutSeconds;

    public AiClient(@Value("${wireforge.ai.base-url}") String baseUrl,
                    @Value("${wireforge.ai.api-key}") String apiKey,
                    @Value("${wireforge.ai.model}") String model,
                    @Value("${wireforge.ai.html-model:}") String htmlModel,
                    @Value("${wireforge.ai.max-output-tokens}") int maxOutputTokens,
                    @Value("${wireforge.ai.timeout-seconds}") int timeoutSeconds) {
        this.baseUrl = trim(baseUrl);
        this.apiKey = trim(apiKey);
        this.model = trim(model);
        this.htmlModel = trim(htmlModel);
        this.maxOutputTokens = maxOutputTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 每次请求新建客户端：中转站/代理会静默关闭空闲连接，复用旧连接会得到
     * "header parser received no bytes" 等传输错误；HTTP/1.1 避免 HTTP/2 流截断问题。
     */
    private HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 图片 + 提示词 → 模型文本输出（使用默认模型）。
     */
    public String generateWithImage(String systemPrompt, String userPrompt,
                                    String imageMime, byte[] imageBytes) {
        return generateWithImageModel(null, systemPrompt, userPrompt, imageMime, imageBytes);
    }

    /** 整页 HTML 生成专用：优先使用 wireforge.ai.html-model 配置的模型（未配置则回退默认模型）。 */
    public String generateHtmlWithImage(String systemPrompt, String userPrompt,
                                        String imageMime, byte[] imageBytes) {
        return generateWithImageModel(htmlModel, systemPrompt, userPrompt, imageMime, imageBytes);
    }

    /** 多图输入（如"参考底图 + 本页设计稿"）：图片按列表顺序进入上下文，供整页 HTML 生成参考 */
    public record ImageInput(String mime, byte[] bytes) {}

    public String generateHtmlWithImages(String systemPrompt, String userPrompt, List<ImageInput> images) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("未配置 AI API Key（wireforge.ai.api-key / 环境变量 AI_API_KEY）");
        }
        List<String> dataUrls = images.stream()
                .map(img -> {
                    byte[] opt = optimizeImageForAi(img.bytes());
                    String mime = (opt != img.bytes()) ? "image/jpeg" : normalizeMime(img.mime());
                    return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(opt);
                })
                .toList();
        return generateWithDataUrls(htmlModel, systemPrompt, userPrompt, dataUrls);
    }

    /**
     * AI 视觉图像智能高清预处理：
     * 解决手机超清长图（2MB~5MB PNG）直传 AI 接口导致 base64 达 4MB+、引发远程中转站 180s 超时断连的问题。
     * 将尺寸超大图在保证 100% 视觉比例与文字清晰度的前提下，等比缩放到最长边 <= 1280px 并输出高质量 JPEG，
     * 体积从 3MB+ 降至 100~200KB，传输与 AI 推理速度提升 10 倍以上，彻底消灭超时卡死。
     */
    private byte[] optimizeImageForAi(byte[] raw) {
        if (raw == null || raw.length <= 400 * 1024) {
            return raw;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(raw);
            BufferedImage img = ImageIO.read(bais);
            if (img == null) return raw;
            int w = img.getWidth(), h = img.getHeight();
            int maxDim = Math.max(w, h);
            if (maxDim <= 1280 && raw.length <= 600 * 1024) {
                return raw;
            }
            double scale = Math.min(1.0, 1280.0 / maxDim);
            int targetW = (int) Math.round(w * scale);
            int targetH = (int) Math.round(h * scale);
            BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetW, targetH);
            g.drawImage(img, 0, 0, targetW, targetH, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            byte[] out = baos.toByteArray();
            log.info("[AI图像预处理] 成功将大图优化传输: {}KB -> {}KB ({}x{} -> {}x{})",
                    raw.length / 1024, out.length / 1024, w, h, targetW, targetH);
            return out;
        } catch (Exception e) {
            log.warn("[AI图像预处理] 压缩失败，回退原图: {}", e.getMessage());
            return raw;
        }
    }

    /** 图片 + 提示词 → 文本输出；modelOverride 为空时使用默认模型。 */
    public String generateWithImageModel(String modelOverride, String systemPrompt, String userPrompt,
                                         String imageMime, byte[] imageBytes) {
        byte[] opt = optimizeImageForAi(imageBytes);
        String mime = (opt != imageBytes) ? "image/jpeg" : normalizeMime(imageMime);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(opt);
        return generateWithDataUrls(modelOverride, systemPrompt, userPrompt, List.of(dataUrl));
    }

    /** 公共生成逻辑：支持单/多图，带线程级执行超时 + 多轮重试 */
    private String generateWithDataUrls(String modelOverride, String systemPrompt, String userPrompt,
                                        List<String> dataUrls) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("未配置 AI API Key（wireforge.ai.api-key / 环境变量 AI_API_KEY）");
        }
        String effectiveModel = modelOverride == null || modelOverride.isBlank() ? model : modelOverride.trim();

        Map<String, Object> chatBody = chatCompletionsBody(effectiveModel, systemPrompt, userPrompt, dataUrls);
        Map<String, Object> responsesBody = responsesBody(effectiveModel, systemPrompt, userPrompt, dataUrls);

        // 代理/TUN 环境下 TLS 流偶发损坏（Tag mismatch / 握手终止），传输层错误做多轮重试。
        // 每次调用包一层"执行超时"：HttpRequest.timeout 只管响应头，SSE 流式读取若被上游
        // 挂起会无限期阻塞并卡死整个分析队列——必须在线程级强制中断。
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return callWithTimeout("/v1/chat/completions", chatBody);
            } catch (Exception e) {
                log.warn("chat-completions 调用失败（第 {} 轮），尝试 responses 兜底: {}", attempt, e.getMessage());
                last = e;
            }
            try {
                return callWithTimeout("/v1/responses", responsesBody);
            } catch (Exception e) {
                log.warn("responses 调用失败（第 {} 轮）: {}", attempt, e.getMessage());
                last = e;
            }
            try {
                Thread.sleep(2000L * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("AI API 请求被中断", ie);
            }
        }
        throw new RuntimeException("AI API 多次重试后仍失败: " + (last != null ? last.getMessage() : "unknown"), last);
    }

    /** 在独立线程中执行 AI 调用，超过 timeoutSeconds 强制中断——防止流式读取被上游挂起后无限阻塞 */
    private String callWithTimeout(String path, Map<String, Object> body) throws Exception {
        FutureTask<String> task = new FutureTask<>(() -> call(path, body));
        Thread worker = new Thread(task, "ai-call-" + path);
        worker.setDaemon(true);
        worker.start();
        try {
            return task.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            task.cancel(true);
            worker.interrupt();
            throw new RuntimeException("AI 调用超时（" + timeoutSeconds + "s），已中断: " + path);
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        }
    }

    private String call(String path, Map<String, Object> body) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;

        // 1. 优先尝试标准非流式同步请求：极速（5~10s完成）、即用即关释放并发连接池，彻底消除 SSE 挂起与 Concurrency limit 假死
        Map<String, Object> syncBody = new LinkedHashMap<>(body);
        syncBody.put("stream", false);
        try {
            String json = objectMapper.writeValueAsString(syncBody);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                String content = extractContent(resp.body().trim());
                if (content != null && !content.isBlank()) {
                    return content.trim();
                }
            }
            if (resp.statusCode() >= 400) {
                log.warn("非流式请求返回状态码 {}: {}，尝试 SSE 流式通道重试", resp.statusCode(), extractError(resp.body()));
            }
        } catch (Exception e) {
            log.warn("非流式同步调用异常: {}，尝试 SSE 流式通道重试", e.getMessage());
        }

        // 2. 流式 SSE 模式兜底
        Map<String, Object> streamBody = new LinkedHashMap<>(body);
        streamBody.put("stream", true);
        String streamJson;
        try {
            streamJson = objectMapper.writeValueAsString(streamBody);
        } catch (Exception e) {
            throw new IllegalStateException("序列化请求失败: " + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(streamJson))
                .build();

        try {
            HttpResponse<java.util.stream.Stream<String>> response =
                    newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                String errorBody = response.body().collect(java.util.stream.Collectors.joining("\n"));
                throw new RuntimeException("AI API 调用失败 (status=" + response.statusCode() + "): " + extractError(errorBody));
            }

            StringBuilder text = new StringBuilder();
            StringBuilder raw = new StringBuilder();
            try (var lines = response.body()) {
                lines.forEach(line -> {
                    raw.append(line).append('\n');
                    handleStreamLine(line, text);
                });
            }
            if (!text.isEmpty()) {
                return text.toString().trim();
            }
            // 部分中转对 stream 请求仍返回完整 JSON，按非流式解析兜底
            return extractContent(raw.toString().trim());
        } catch (IOException e) {
            throw new RuntimeException("AI API 请求异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI API 请求被中断", e);
        }
    }

    /**
     * 解析单行 SSE：兼容 chat-completions（choices[].delta.content）与
     * responses（response.output_text.delta）两种事件格式。
     */
    private void handleStreamLine(String line, StringBuilder text) {
        if (line == null || line.isBlank()) return;
        String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
        if (payload.isBlank() || "[DONE]".equals(payload) || !payload.startsWith("{")) return;
        try {
            JsonNode node = objectMapper.readTree(payload);
            String errorMessage = node.path("error").path("message").asText("");
            if (!errorMessage.isBlank()) throw new RuntimeException(errorMessage);

            String type = node.path("type").asText("");
            if ("response.failed".equals(type) || "response.error".equals(type)) {
                String message = node.path("message").asText("");
                if (message.isBlank()) message = node.path("response").path("error").path("message").asText("");
                throw new RuntimeException(message.isBlank() ? "AI 流式响应失败" : message);
            }
            if ("response.output_text.delta".equals(type)) {
                text.append(node.path("delta").asText(""));
                return;
            }
            JsonNode choices = node.path("choices");
            if (choices.isArray()) {
                for (JsonNode choice : choices) {
                    String delta = extractMessageText(choice.path("delta").path("content"));
                    if (delta.isBlank()) delta = extractMessageText(choice.path("message").path("content"));
                    if (!delta.isBlank()) text.append(delta);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception ignore) {
            // 无法解析的心跳/元数据行，跳过
        }
    }

    private Map<String, Object> chatCompletionsBody(String modelName, String systemPrompt, String userPrompt, List<String> dataUrls) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", userPrompt));
        for (String dataUrl : dataUrls) {
            content.add(Map.of("type", "image_url",
                    "image_url", Map.of("url", dataUrl, "detail", "high")));
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("max_tokens", maxOutputTokens);
        body.put("temperature", 0.1);
        return body;
    }

    private Map<String, Object> responsesBody(String modelName, String systemPrompt, String userPrompt, List<String> dataUrls) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text", userPrompt));
        for (String dataUrl : dataUrls) {
            content.add(Map.of("type", "input_image", "image_url", dataUrl, "detail", "high"));
        }

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("instructions", systemPrompt);
        body.put("input", List.of(userMessage));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("store", false);
        return body;
    }

    private String extractContent(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String errorMessage = root.path("error").path("message").asText("");
            if (!errorMessage.isBlank()) throw new RuntimeException(errorMessage);

            String outputText = root.path("output_text").asText("");
            if (!outputText.isBlank()) return outputText;

            StringBuilder sb = new StringBuilder();
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) appendText(sb, item.path("content"));
            }
            if (!sb.isEmpty()) return sb.toString().trim();

            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                String content = extractMessageText(message.path("content"));
                if (!content.isBlank()) return content;
            }
            return responseJson;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 响应解析失败，返回原始文本: {}", e.getMessage());
            return responseJson;
        }
    }

    private void appendText(StringBuilder sb, JsonNode content) {
        if (!content.isArray()) return;
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("output_text".equals(type) || "text".equals(type)) {
                String text = block.path("text").asText("");
                if (!text.isBlank()) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(text);
                }
            }
        }
    }

    private String extractMessageText(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) return "";
        if (contentNode.isTextual()) return contentNode.asText("");
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : contentNode) {
                if (block.isTextual()) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(block.asText());
                    continue;
                }
                String type = block.path("type").asText("");
                String text = block.path("text").asText("");
                if (text.isBlank()) text = block.path("content").asText("");
                if (!text.isBlank() && (type.isBlank() || "text".equals(type) || "output_text".equals(type))) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(text);
                }
            }
            return sb.toString().trim();
        }
        if (contentNode.isObject()) {
            String text = contentNode.path("text").asText("");
            if (!text.isBlank()) return text;
            return contentNode.path("content").asText("");
        }
        return "";
    }

    private String extractError(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String message = root.path("error").path("message").asText("");
            if (!message.isBlank()) return message;
            return root.path("message").asText("");
        } catch (Exception e) {
            return responseJson;
        }
    }

    private static String normalizeMime(String mime) {
        String m = trim(mime).toLowerCase();
        if ("image/jpg".equals(m) || "image/jpeg".equals(m)) return "image/jpeg";
        if (m.startsWith("image/")) return m;
        return "image/png";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}