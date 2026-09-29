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
     * 纯文本输入 → 模型文本输出（阶段二轻量级交互路由推导专用）。
     * 不携带任何图片，耗费极低 Token，速度快（秒级返回），无超时断开风险。
     */
    public String generateText(String systemPrompt, String userPrompt) {
        return generateWithDataUrls(null, systemPrompt, userPrompt, List.of());
    }

    /**
     * AI 视觉图像智能高清预处理结果
     */
    public record PreparedImage(byte[] bytes, String mime, int width, int height) {}

    /**
     * 智能压缩预处理：在不改变长宽比的前提下，将超大尺寸或高体积设计稿
     * 降采样至最大边 <= 1280px、体积适中的 JPEG，彻底避免超时与大额 Token 浪费。
     * 同时返回实际发送给大模型的真实物理宽高，确保 Prompt 声明与坐标缩放 100% 吻合。
     */
    public static PreparedImage prepareImage(byte[] raw, String rawMime) {
        if (raw == null) {
            return new PreparedImage(new byte[0], "image/png", 0, 0);
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(raw);
            BufferedImage img = ImageIO.read(bais);
            if (img == null) {
                return new PreparedImage(raw, normalizeMime(rawMime), 0, 0);
            }
            int w = img.getWidth(), h = img.getHeight();
            int maxDim = Math.max(w, h);
            if (maxDim <= 1080 && raw.length <= 500 * 1024) {
                return new PreparedImage(raw, normalizeMime(rawMime), w, h);
            }
            double scale = Math.min(1.0, 1080.0 / maxDim);
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
            return new PreparedImage(out, "image/jpeg", targetW, targetH);
        } catch (Exception e) {
            log.warn("[AI图像预处理] 压缩失败，回退原图: {}", e.getMessage());
            return new PreparedImage(raw, normalizeMime(rawMime), 0, 0);
        }
    }

    private byte[] optimizeImageForAi(byte[] raw) {
        return prepareImage(raw, "image/png").bytes();
    }

    /** 图片 + 提示词 → 文本输出；modelOverride 为空时使用默认模型。 */
    public String generateWithImageModel(String modelOverride, String systemPrompt, String userPrompt,
                                         String imageMime, byte[] imageBytes) {
        PreparedImage prep = prepareImage(imageBytes, imageMime);
        String dataUrl = "data:" + prep.mime() + ";base64," + Base64.getEncoder().encodeToString(prep.bytes());
        return generateWithDataUrls(modelOverride, systemPrompt, userPrompt, List.of(dataUrl));
    }

    /**
     * 一次调用失败时是否值得再发：只有确定上游还没开始算（连不上、限流、网关拒绝）才重发。
     * 超时或读到一半断开时上游多半已经计费，重发会把同一张图再算一遍。
     */
    static final class AiCallException extends RuntimeException {
        final boolean retryable;
        /** 该接口路径不被中转站支持，可以换另一种接口 */
        final boolean unsupported;

        AiCallException(String message, boolean retryable, boolean unsupported, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
            this.unsupported = unsupported;
        }
    }

    /** 公共生成逻辑：支持单/多图，带线程级执行超时；超时不重发，只有没连上时重试一次 */
    private String generateWithDataUrls(String modelOverride, String systemPrompt, String userPrompt,
                                        List<String> dataUrls) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("未配置 AI API Key（wireforge.ai.api-key / 环境变量 AI_API_KEY）");
        }
        String effectiveModel = modelOverride == null || modelOverride.isBlank() ? model : modelOverride.trim();
        String label = USAGE_LABEL.get();

        Map<String, Object> chatBody = chatCompletionsBody(effectiveModel, systemPrompt, userPrompt, dataUrls);
        Map<String, Object> responsesBody = responsesBody(effectiveModel, systemPrompt, userPrompt, dataUrls);

        // 每次调用包一层"执行超时"：HttpRequest.timeout 只管响应头，SSE 流式读取若被上游
        // 挂起会无限期阻塞并卡死整个分析队列——必须在线程级强制中断。
        // 严格遵循规范：未收到字节的连接握手丢包才重连；一旦开始传输或超时，绝不自动重发，避免重复扣 token。
        try {
            try {
                return callWithTimeout("/v1/chat/completions", chatBody, label, effectiveModel);
            } catch (AiCallException e) {
                if (e.retryable) {
                    log.warn("[AI连接] 物理握手/连接在建立前中断（0 字节抵达，无计费风险），等待 1.5s 后握手重试: {}", e.getMessage());
                    try { Thread.sleep(1500); } catch (InterruptedException ignore) {}
                    return callWithTimeout("/v1/chat/completions", chatBody, label, effectiveModel);
                }
                if (!e.unsupported) throw e;
                log.warn("chat-completions 不可用（{}），改用 responses", e.getMessage());
                return callWithTimeout("/v1/responses", responsesBody, label, effectiveModel);
            }
        } catch (AiCallException e) {
            if (e.retryable) {
                log.warn("[AI连接] 物理握手再次中断（0 字节抵达，无计费风险），等待 1.5s 后进行最后一次握手重试: {}", e.getMessage());
                try { Thread.sleep(1500); } catch (InterruptedException ignore) {}
                try {
                    return callWithTimeout("/v1/chat/completions", chatBody, label, effectiveModel);
                } catch (AiCallException finalEx) {
                    throw new RuntimeException("AI 连接中断（已终止，未重复计费）: " + finalEx.getMessage(), finalEx);
                }
            }
            throw new RuntimeException("AI 调用失败（未自动重发，避免重复计费）: " + e.getMessage(), e);
        }
    }

    /** 调用方可设置一个说明（如"识别 首页"），写进用量日志，方便看是哪一页耗的 token */
    private static final ThreadLocal<String> USAGE_LABEL = new ThreadLocal<>();

    public static void setUsageLabel(String label) {
        if (label == null) USAGE_LABEL.remove();
        else USAGE_LABEL.set(label);
    }

    /** 在独立线程中执行 AI 调用，超过 timeoutSeconds 强制中断——防止流式读取被上游挂起后无限阻塞 */
    private String callWithTimeout(String path, Map<String, Object> body, String label, String modelName) {
        long started = System.currentTimeMillis();
        FutureTask<String> task = new FutureTask<>(() -> call(path, body, label, modelName, started));
        Thread worker = new Thread(task, "ai-call-" + path);
        worker.setDaemon(true);
        worker.start();
        try {
            return task.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            task.cancel(true);
            worker.interrupt();
            log.warn("[AI用量] {} 超时 {}s 已中断，上游可能已按这次请求计费", labelText(label), timeoutSeconds);
            throw new AiCallException("AI 调用超时（" + timeoutSeconds + "s），已中断: " + path, false, false, te);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            worker.interrupt();
            throw new AiCallException("AI API 请求被中断", false, false, ie);
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof AiCallException ace) throw ace;
            throw new AiCallException(cause == null ? "未知错误" : String.valueOf(cause.getMessage()), false, false, cause);
        }
    }

    /** 按状态码判断：404/405 等说明接口不支持，可换另一种；429/502/503 说明没开始算，可重试 */
    private AiCallException statusError(int status, String body) {
        String msg = "AI API 调用失败 (status=" + status + "): " + extractError(body);
        boolean unsupported = status == 404 || status == 405 || status == 415 || status == 501;
        boolean retryable = status == 429 || status == 502 || status == 503;
        return new AiCallException(msg, retryable, unsupported, null);
    }

    /** 发送阶段就失败（连不上、握手失败）或未收到任何字节可以重试；等响应时超时说明上游已在算，不重试 */
    private static AiCallException ioError(IOException e, boolean receivedAnyBytes) {
        boolean notConnected = !receivedAnyBytes
                || e instanceof java.net.http.HttpConnectTimeoutException
                || e instanceof java.net.ConnectException
                || e instanceof java.net.UnknownHostException
                || e instanceof javax.net.ssl.SSLHandshakeException;
        return new AiCallException("AI API 请求异常: " + e.getMessage(), notConnected, false, e);
    }

    private String call(String path, Map<String, Object> body, String label, String modelName, long started) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;

        // 1. 优先流式。非流式要等整段写完才有响应头，中转站大约 1 分钟收不到头就会掐断，
        //    上游往往已经开始计费。流式会先返回头，内容边到边收，失败不再补发第二次。
        AiCallException streamFailure;
        try {
            return callStream(path, body, label, modelName, started);
        } catch (AiCallException e) {
            if (!e.unsupported) throw e;
            streamFailure = e;
            log.warn("流式被拒绝，改为一次非流式: {}", e.getMessage());
        }

        // 2. 只有中转站明确不接受流式时才走非流式，仍然只发这一次。
        Map<String, Object> syncBody = new LinkedHashMap<>(body);
        syncBody.put("stream", false);
        HttpResponse<String> resp;
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
            resp = newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw ioError(e, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException("AI API 请求被中断", false, false, e);
        }

        String syncText = resp.body() == null ? "" : resp.body().trim();
        if (resp.statusCode() >= 400) {
            if (reasoningRejected(syncText) && stripReasoning(syncBody)) {
                log.warn("中转站不接受推理档位参数，去掉后重发一次");
                return call(path, syncBody, label, modelName, started);
            }
            if (temperatureRejected(syncText) && stripTemperature(syncBody)) {
                log.warn("中转站不接受 temperature 参数，去掉后重发一次");
                return call(path, syncBody, label, modelName, started);
            }
            throw statusError(resp.statusCode(), syncText);
        } else if (!syncText.isEmpty()) {
            if (syncText.startsWith("{")) {
                Usage usage = new Usage();
                try {
                    usage.read(objectMapper.readTree(syncText).path("usage"));
                } catch (Exception ignore) {
                    // 用量读不到不影响结果
                }
                usage.log(label, modelName, path, started);
                String content = extractContent(syncText);
                if (content != null && !content.isBlank() && !content.equals(syncText)) {
                    return content.trim();
                }
                throw new AiCallException("AI 返回里没有文本内容（已计费，未重发）", false, false, null);
            }
            // 部分中转对非流式请求直接回 SSE 文本：按流式行解析，不再重发
            StringBuilder parsed = new StringBuilder();
            Usage usage = new Usage();
            for (String line : syncText.split("\n")) handleStreamLine(line, parsed, usage);
            usage.log(label, modelName, path, started);
            if (!parsed.isEmpty()) return parsed.toString().trim();
            return syncText;
        } else {
            throw new AiCallException("AI 返回了空响应（未重发）", false, false, streamFailure);
        }
    }

    /** 流式发送一次。中转站明确拒绝流式时标记为可改走非流式；其余失败直接抛出，不再补发。 */
    private String callStream(String path, Map<String, Object> body, String label, String modelName, long started) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
        Map<String, Object> streamBody = new LinkedHashMap<>(body);
        streamBody.put("stream", true);
        if (body.containsKey("messages")) {
            streamBody.put("stream_options", Map.of("include_usage", true));
        }
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

        boolean[] hasBytes = new boolean[]{false};
        try {
            HttpResponse<java.util.stream.Stream<String>> response =
                    newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                String errorBody = response.body().collect(java.util.stream.Collectors.joining("\n"));
                if (reasoningRejected(errorBody) && stripReasoning(body)) {
                    log.warn("中转站不接受推理档位参数，去掉后重发一次");
                    return callStream(path, body, label, modelName, started);
                }
                if (temperatureRejected(errorBody) && stripTemperature(body)) {
                    log.warn("中转站不接受 temperature 参数，去掉后重发一次");
                    return callStream(path, body, label, modelName, started);
                }
                if (streamRejected(errorBody)) {
                    throw new AiCallException(errorBody, false, true, null);
                }
                throw statusError(response.statusCode(), errorBody);
            }

            StringBuilder text = new StringBuilder();
            StringBuilder raw = new StringBuilder();
            Usage usage = new Usage();
            try (var lines = response.body()) {
                lines.forEach(line -> {
                    hasBytes[0] = true;
                    raw.append(line).append('\n');
                    handleStreamLine(line, text, usage);
                });
            }
            if (!text.isEmpty()) {
                usage.log(label, modelName, path, started);
                return text.toString().trim();
            }
            String rawText = raw.toString().trim();
            try {
                usage.read(objectMapper.readTree(rawText).path("usage"));
            } catch (Exception ignore) {
                // 不是完整 JSON
            }
            usage.log(label, modelName, path, started);
            String content = extractContent(rawText);
            if (content == null || content.isBlank()) {
                throw new AiCallException("AI 流式返回为空（已连接，未重发）", false, false, null);
            }
            return content;
        } catch (AiCallException e) {
            throw e;
        } catch (IOException e) {
            throw ioError(e, hasBytes[0]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException("AI API 请求被中断", false, false, e);
        }
    }

    /** 400 且明确表示不要流式，才允许改走一次非流式。 */
    private static boolean streamRejected(String body) {
        if (body == null) return false;
        String text = body.toLowerCase();
        boolean mentionsStream = text.contains("stream") || body.contains("流式");
        boolean rejected = text.contains("not support") || text.contains("unsupported")
                || text.contains("disable") || text.contains("must be false")
                || body.contains("不支持") || body.contains("非流");
        return mentionsStream && rejected;
    }

    private static boolean reasoningRejected(String body) {
        if (body == null) return false;
        String text = body.toLowerCase();
        return text.contains("reasoning");
    }

    /** 去掉推理档位参数。返回是否真的去掉了，避免失败后反复重发。 */
    private static boolean stripReasoning(Map<String, Object> body) {
        boolean removed = body.remove("reasoning_effort") != null;
        removed = body.remove("reasoning") != null || removed;
        return removed;
    }

    private static boolean temperatureRejected(String body) {
        if (body == null) return false;
        String text = body.toLowerCase();
        return text.contains("temperature");
    }

    private static boolean stripTemperature(Map<String, Object> body) {
        return body.remove("temperature") != null;
    }

    /** 一次调用的 token 用量；中转站没返回时各项为 -1 */
    private static final class Usage {
        long input = -1;
        long output = -1;
        long reasoning = -1;
        long cached = -1;

        void read(JsonNode u) {
            if (u == null || u.isMissingNode() || u.isNull() || !u.isObject()) return;
            if (u.has("prompt_tokens")) input = u.path("prompt_tokens").asLong(-1);
            if (u.has("input_tokens")) input = u.path("input_tokens").asLong(-1);
            if (u.has("completion_tokens")) output = u.path("completion_tokens").asLong(-1);
            if (u.has("output_tokens")) output = u.path("output_tokens").asLong(-1);
            JsonNode outDetail = u.has("completion_tokens_details") ? u.path("completion_tokens_details") : u.path("output_tokens_details");
            if (outDetail.has("reasoning_tokens")) reasoning = outDetail.path("reasoning_tokens").asLong(-1);
            JsonNode inDetail = u.has("prompt_tokens_details") ? u.path("prompt_tokens_details") : u.path("input_tokens_details");
            if (inDetail.has("cached_tokens")) cached = inDetail.path("cached_tokens").asLong(-1);
        }

        void log(String label, String modelName, String path, long started) {
            long secs = Math.round((System.currentTimeMillis() - started) / 1000.0);
            if (input < 0 && output < 0) {
                AiClient.log.info("[AI用量] {} 模型={} 接口={} 耗时={}s（中转站未返回用量）",
                        labelText(label), modelName, path, secs);
                return;
            }
            AiClient.log.info("[AI用量] {} 模型={} 输入={} 输出={} 其中推理={} 缓存命中={} 耗时={}s",
                    labelText(label), modelName, fmt(input), fmt(output), fmt(reasoning), fmt(cached), secs);
        }

        private static String fmt(long v) {
            return v < 0 ? "-" : String.valueOf(v);
        }
    }

    private static String labelText(String label) {
        return label == null || label.isBlank() ? "(未标注)" : label;
    }

    /**
     * 解析单行 SSE：兼容 chat-completions（choices[].delta.content）与
     * responses（response.output_text.delta）两种事件格式。
     */
    private void handleStreamLine(String line, StringBuilder text, Usage usage) {
        if (line == null || line.isBlank()) return;
        String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
        if (payload.isBlank() || "[DONE]".equals(payload) || !payload.startsWith("{")) return;
        try {
            JsonNode node = objectMapper.readTree(payload);
            String errorMessage = node.path("error").path("message").asText("");
            if (!errorMessage.isBlank()) throw new RuntimeException(errorMessage);

            if (node.has("usage")) usage.read(node.path("usage"));
            if (node.path("response").has("usage")) usage.read(node.path("response").path("usage"));

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
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        if (dataUrls == null || dataUrls.isEmpty()) {
            messages.add(Map.of("role", "user", "content", userPrompt));
        } else {
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "text", "text", userPrompt));
            for (String dataUrl : dataUrls) {
                content.add(Map.of("type", "image_url",
                        "image_url", Map.of("url", dataUrl, "detail", "high")));
            }
            messages.add(Map.of("role", "user", "content", content));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("max_tokens", maxOutputTokens);
        body.put("temperature", 0.1);
        body.put("reasoning_effort", "low");
        return body;
    }

    private Map<String, Object> responsesBody(String modelName, String systemPrompt, String userPrompt, List<String> dataUrls) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");

        if (dataUrls == null || dataUrls.isEmpty()) {
            userMessage.put("content", userPrompt);
        } else {
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "input_text", "text", userPrompt));
            for (String dataUrl : dataUrls) {
                content.add(Map.of("type", "input_image", "image_url", dataUrl, "detail", "high"));
            }
            userMessage.put("content", content);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("instructions", systemPrompt);
        body.put("input", List.of(userMessage));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("reasoning", Map.of("effort", "low"));
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