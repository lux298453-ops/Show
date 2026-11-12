package com.wireforge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
 * OpenAI 兼容客户端；可配置优先使用 Chat Completions 或 Responses。
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
    // One client/pool per Spring singleton; creating one per request also creates
    // a selector manager and makes resource reclamation depend on GC.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    @Value("${wireforge.ai.html-timeout-seconds:600}")
    private int htmlTimeoutSeconds = 600;
    @Value("${wireforge.ai.protocol:chat-completions}")
    private String protocol = "chat-completions";
    @Value("${wireforge.ai.html-image-max-edge:2400}")
    private int htmlImageMaxEdge = 2400;
    @Value("${wireforge.ai.html-reasoning-effort:high}")
    private String htmlReasoningEffort = "high";

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

    @PostConstruct
    void logConfiguration() {
        log.info("[AI配置] host={} 优先协议={} 识别模型={} HTML模型={}", URI.create(baseUrl).getHost(),
                protocol, model, htmlModel.isBlank() ? model : htmlModel);
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
        PreparedImage image = prepareImage(imageBytes, imageMime, htmlImageMaxEdge);
        String dataUrl = "data:" + image.mime() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
        return generateWithDataUrls(htmlModel, systemPrompt, userPrompt, List.of(dataUrl), true);
    }

    /** 多图输入（如"参考底图 + 本页设计稿"）：图片按列表顺序进入上下文，供整页 HTML 生成参考 */
    public record ImageInput(String mime, byte[] bytes) {}

    public String generateHtmlWithImages(String systemPrompt, String userPrompt, List<ImageInput> images) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("未配置 AI API Key（wireforge.ai.api-key / 环境变量 AI_API_KEY）");
        }
        List<String> dataUrls = images.stream()
                .map(img -> {
                    PreparedImage image = prepareImage(img.bytes(), img.mime(), htmlImageMaxEdge);
                    return "data:" + image.mime() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
                })
                .toList();
        return generateWithDataUrls(htmlModel, systemPrompt, userPrompt, dataUrls, true);
    }

    /**
     * 纯文本输入 → 模型文本输出（阶段二轻量级交互路由推导专用）。
     * 不携带任何图片，耗费极低 Token，速度快（秒级返回），无超时断开风险。
     */
    public String generateText(String systemPrompt, String userPrompt) {
        return generateWithDataUrls(null, systemPrompt, userPrompt, List.of());
    }

    /**
     * 纯文本输入（HTML 增强模型优先）→ 模型文本/JSON 输出，供全屏演示 AI 原型微调专用。
     */
    public String generateHtmlText(String systemPrompt, String userPrompt) {
        return generateWithDataUrls(htmlModel != null && !htmlModel.isBlank() ? htmlModel : null, systemPrompt, userPrompt, List.of(), true);
    }

    /**
     * AI 视觉图像智能高清预处理结果
     */
    public record PreparedImage(byte[] bytes, String mime, int width, int height) {}

    /**
     * 智能压缩预处理：在不改变长宽比的前提下，将超大尺寸或高体积设计稿
     * 识别阶段最长边1080px；HTML阶段保留更高分辨率，避免小卡片的角色细节丢失。
     * 同时返回实际发送给大模型的真实物理宽高，确保 Prompt 声明与坐标缩放 100% 吻合。
     */
    public static PreparedImage prepareImage(byte[] raw, String rawMime) {
        return prepareImage(raw, rawMime, 1080);
    }

    static PreparedImage prepareImage(byte[] raw, String rawMime, int maxEdge) {
        maxEdge = Math.max(1080, Math.min(3840, maxEdge));
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
            if (maxDim <= maxEdge && raw.length <= (maxEdge > 1080 ? 4 * 1024 * 1024 : 500 * 1024)) {
                return new PreparedImage(raw, normalizeMime(rawMime), w, h);
            }
            double scale = Math.min(1.0, (double) maxEdge / maxDim);
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

    /** 图片 + 提示词 → 文本输出；modelOverride 为空时使用默认模型。 */
    public String generateWithImageModel(String modelOverride, String systemPrompt, String userPrompt,
                                         String imageMime, byte[] imageBytes) {
        PreparedImage prep = prepareImage(imageBytes, imageMime);
        String dataUrl = "data:" + prep.mime() + ";base64," + Base64.getEncoder().encodeToString(prep.bytes());
        return generateWithDataUrls(modelOverride, systemPrompt, userPrompt, List.of(dataUrl));
    }

    /**
     * 一次调用失败时是否值得再发：只有确定连接未建立才重发。
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
        return generateWithDataUrls(modelOverride, systemPrompt, userPrompt, dataUrls, false);
    }

    private String generateWithDataUrls(String modelOverride, String systemPrompt, String userPrompt,
                                        List<String> dataUrls, boolean htmlGeneration) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("未配置 AI API Key（wireforge.ai.api-key / 环境变量 AI_API_KEY）");
        }
        String effectiveModel = modelOverride == null || modelOverride.isBlank() ? model : modelOverride.trim();
        int requestTimeout = htmlGeneration ? Math.max(timeoutSeconds, htmlTimeoutSeconds) : timeoutSeconds;
        String label = USAGE_LABEL.get();

        Map<String, Object> chatBody = chatCompletionsBody(effectiveModel, systemPrompt, userPrompt, dataUrls);
        Map<String, Object> responsesBody = responsesBody(effectiveModel, systemPrompt, userPrompt, dataUrls);
        if (htmlGeneration) {
            String effort = List.of("low", "medium", "high").contains(htmlReasoningEffort) ? htmlReasoningEffort : "high";
            chatBody.put("reasoning_effort", effort);
            responsesBody.put("reasoning", Map.of("effort", effort));
        }
        boolean responsesFirst = "responses".equalsIgnoreCase(protocol);
        String primaryPath = responsesFirst ? "/v1/responses" : "/v1/chat/completions";
        String fallbackPath = responsesFirst ? "/v1/chat/completions" : "/v1/responses";
        Map<String, Object> primaryBody = responsesFirst ? responsesBody : chatBody;
        Map<String, Object> fallbackBody = responsesFirst ? chatBody : responsesBody;

        // 每次调用包一层"执行超时"：HttpRequest.timeout 只管响应头，SSE 流式读取若被上游
        // 挂起会无限期阻塞并卡死整个分析队列——必须在线程级强制中断。
        // 没收到响应头不代表请求未被处理；只有明确的建连失败才允许重试。
        try {
            try {
                return callWithConnectionRetry(primaryPath, primaryBody, label, effectiveModel, requestTimeout);
            } catch (AiCallException e) {
                if (!e.unsupported) throw e;
                log.warn("{} 不可用（{}），改用 {}", primaryPath, e.getMessage(), fallbackPath);
                return callWithConnectionRetry(fallbackPath, fallbackBody, label, effectiveModel, requestTimeout);
            }
        } catch (AiCallException e) {
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        }
    }

    private String callWithConnectionRetry(String path, Map<String, Object> body, String label,
                                           String modelName, int requestTimeout) {
        try {
            return callWithTimeout(path, body, label, modelName, requestTimeout);
        } catch (AiCallException e) {
            if (!e.retryable) throw e;
            log.warn("[AI连接] {} 连接未建立，等待 1.5s 后重试一次: {}", labelText(label), e.getMessage());
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AiCallException("AI API 请求被中断", false, false, interrupted);
            }
            return callWithTimeout(path, body, label, modelName, requestTimeout);
        }
    }

    /** 调用方可设置一个说明（如"识别 首页"），写进用量日志，方便看是哪一页耗的 token */
    private static final ThreadLocal<String> USAGE_LABEL = new ThreadLocal<>();

    public static void setUsageLabel(String label) {
        if (label == null) USAGE_LABEL.remove();
        else USAGE_LABEL.set(label);
    }

    /** 在独立线程中执行 AI 调用，超过 timeoutSeconds 强制中断——防止流式读取被上游挂起后无限阻塞 */
    private String callWithTimeout(String path, Map<String, Object> body, String label, String modelName, int requestTimeout) {
        long started = System.currentTimeMillis();
        FutureTask<String> task = new FutureTask<>(() -> call(path, body, label, modelName, started, requestTimeout));
        Thread worker = new Thread(task, "ai-call-" + path);
        worker.setDaemon(true);
        worker.start();
        try {
            return task.get(requestTimeout, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            task.cancel(true);
            worker.interrupt();
            log.warn("[AI用量] {} 超时 {}s 已中断，上游可能已按这次请求计费", labelText(label), requestTimeout);
            throw new AiCallException("AI 调用超时（" + requestTimeout + "s），已中断: " + path, false, false, te);
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

    /** HTTP 网关错误不能证明上游未处理请求，不自动重发；仅明确不支持接口时切换协议。 */
    private AiCallException statusError(int status, String body) {
        String msg = "AI API 调用失败 (status=" + status + "): " + extractError(body);
        boolean unsupported = status == 404 || status == 405 || status == 415 || status == 501;
        return new AiCallException(msg, false, unsupported, null);
    }

    /** 没收到正文或响应头仍可能已发出POST，不能据此重试。 */
    static AiCallException ioError(IOException e) {
        boolean notConnected = e instanceof java.net.http.HttpConnectTimeoutException
                || e instanceof java.net.ConnectException
                || e instanceof java.net.UnknownHostException
                || e instanceof javax.net.ssl.SSLHandshakeException;
        return new AiCallException("AI API 请求异常: " + e.getMessage(), notConnected, false, e);
    }

    private String call(String path, Map<String, Object> body, String label, String modelName, long started, int requestTimeout) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;

        // 1. 优先流式以接收进度。连接截断和超时均不能证明未计费，不补发。
        try {
            return callStream(path, body, label, modelName, started, requestTimeout);
        } catch (AiCallException e) {
            if (!e.unsupported) throw e;
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
                    .timeout(Duration.ofSeconds(requestTimeout))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw ioError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException("AI API 请求被中断", false, false, e);
        }

        String syncText = resp.body() == null ? "" : resp.body().trim();
        if (resp.statusCode() >= 400) {
            if (parameterRejected(resp.statusCode(), syncText, "reasoning") && stripReasoning(syncBody)) {
                log.warn("中转站不接受推理档位参数，去掉后重发一次");
                return call(path, syncBody, label, modelName, started, requestTimeout);
            }
            if (parameterRejected(resp.statusCode(), syncText, "temperature") && stripTemperature(syncBody)) {
                log.warn("中转站不接受 temperature 参数，去掉后重发一次");
                return call(path, syncBody, label, modelName, started, requestTimeout);
            }
            throw statusError(resp.statusCode(), syncText);
        }
        // 有的中转站即使接受 stream=false，仍返回 SSE；统一按内容解码，不补发请求。
        ResponseReader reader = new ResponseReader();
        syncText.lines().forEach(reader::line);
        String content = reader.result();
        reader.usage.log(label, modelName, path, started);
        return content;
    }

    /** 流式发送一次。中转站明确拒绝流式时标记为可改走非流式；其余失败直接抛出，不再补发。 */
    private String callStream(String path, Map<String, Object> body, String label, String modelName, long started, int requestTimeout) {
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
                .timeout(Duration.ofSeconds(requestTimeout))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(streamJson))
                .build();

        ResponseReader reader = new ResponseReader();
        int responseStatus = -1;
        String contentType = "unreceived";
        try {
            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            responseStatus = response.statusCode();
            contentType = diagnosticToken(response.headers().firstValue("Content-Type").orElse("missing").split(";", 2)[0]);
            log.info("[AI响应头] {} 接口={} HTTP={} 类型={} 等待={}ms 请求字符={}", labelText(label),
                    path, responseStatus, contentType, System.currentTimeMillis() - started, streamJson.length());
            if (response.statusCode() >= 400) {
                String errorBody;
                try (var lines = response.body()) {
                    errorBody = lines.collect(java.util.stream.Collectors.joining("\n"));
                }
                if (parameterRejected(responseStatus, errorBody, "reasoning") && stripReasoning(body)) {
                    log.warn("中转站不接受推理档位参数，去掉后重发一次");
                    return callStream(path, body, label, modelName, started, requestTimeout);
                }
                if (parameterRejected(responseStatus, errorBody, "temperature") && stripTemperature(body)) {
                    log.warn("中转站不接受 temperature 参数，去掉后重发一次");
                    return callStream(path, body, label, modelName, started, requestTimeout);
                }
                if ((responseStatus == 400 || responseStatus == 422) && streamRejected(errorBody)) {
                    throw new AiCallException("中转站明确不支持 stream 参数", false, true, null);
                }
                throw statusError(response.statusCode(), errorBody);
            }

            try (var lines = response.body()) {
                var iterator = lines.iterator();
                while (!reader.terminal && iterator.hasNext()) {
                    reader.line(iterator.next());
                }
            }
            String content = reader.result();
            reader.usage.log(label, modelName, path, started);
            return content;
        } catch (AiCallException e) {
            logStreamFailure(label, path, started, responseStatus, contentType, reader, "protocol_or_upstream");
            throw e;
        } catch (java.io.UncheckedIOException e) {
            logStreamFailure(label, path, started, responseStatus, contentType, reader, "body_io");
            throw new AiCallException("AI 流式响应中断（连接读取失败，未收到完成事件，未重发）", false, false, e);
        } catch (IOException e) {
            logStreamFailure(label, path, started, responseStatus, contentType, reader, "connection_io");
            throw ioError(e);
        } catch (InterruptedException e) {
            logStreamFailure(label, path, started, responseStatus, contentType, reader, "interrupted");
            Thread.currentThread().interrupt();
            throw new AiCallException("AI API 请求被中断", false, false, e);
        }
    }

    private void logStreamFailure(String label, String path, long started, int status, String contentType,
                                  ResponseReader reader, String category) {
        // Only transport metadata and counters. Never log response frames, prompts, HTML or images.
        log.warn("[AI响应失败] {} 接口={} HTTP={} 类型={} 耗时={}ms 行数={} 接收字符={} 事件数={} 正文字符={} 最后事件={} 完成={} 类别={}",
                labelText(label), path, status, contentType, System.currentTimeMillis() - started,
                reader.lineCount, reader.receivedCharacters, reader.frameCount, reader.textLength(),
                reader.lastEvent, reader.completed, category);
    }

    private static String diagnosticToken(String value) {
        return value != null && value.matches("[a-zA-Z0-9_./:-]{1,96}") ? value : "unrecognized";
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

    private static boolean parameterRejected(int status, String body, String parameter) {
        if ((status != 400 && status != 422) || body == null) return false;
        String text = body.toLowerCase(java.util.Locale.ROOT);
        boolean rejected = text.contains("not support") || text.contains("unsupported")
                || text.contains("unknown parameter") || text.contains("unrecognized")
                || text.contains("not allowed") || text.contains("not permitted")
                || text.contains("invalid parameter") || body.contains("不支持");
        return text.contains(parameter) && rejected;
    }

    /** 去掉推理档位参数。返回是否真的去掉了，避免失败后反复重发。 */
    private static boolean stripReasoning(Map<String, Object> body) {
        boolean removed = body.remove("reasoning_effort") != null;
        removed = body.remove("reasoning") != null || removed;
        return removed;
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

    /** Read SSE frames, not individual JSON lines. Relays may omit deltas and send only final snapshots. */
    private final class ResponseReader {
        final Usage usage = new Usage();
        final StringBuilder raw = new StringBuilder();
        final StringBuilder data = new StringBuilder();
        final java.util.SortedMap<Integer, java.util.SortedMap<Integer, StringBuilder>> parts = new java.util.TreeMap<>();
        String event = "";
        boolean sse;
        boolean completed;
        boolean terminal;
        boolean firstLine = true;
        long lineCount;
        long receivedCharacters;
        long frameCount;
        String lastEvent = "none";

        void line(String line) {
            if (terminal) return;
            lineCount++;
            receivedCharacters += line.length();
            if (firstLine) {
                line = line.startsWith("\uFEFF") ? line.substring(1) : line;
                firstLine = false;
            }
            if (line.isEmpty()) {
                dispatch();
                return;
            }
            if (line.startsWith(":")) { sse = true; raw.setLength(0); return; }
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            if (List.of("data", "event", "id", "retry").contains(field)) {
                sse = true;
                raw.setLength(0);
                if ("data".equals(field)) data.append(value).append('\n');
                if ("event".equals(field)) event = value;
            } else if (!sse) {
                raw.append(line).append('\n');
            }
        }

        void dispatch() {
            if (!data.isEmpty()) {
                frameCount++;
                String payload = data.toString().stripTrailing();
                data.setLength(0);
                if ("[DONE]".equals(payload)) {
                    lastEvent = "DONE";
                    completed = terminal = true;
                } else {
                    accept(parse(payload), event);
                }
            }
            event = "";
        }

        JsonNode parse(String payload) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                if (node != null && node.isObject()) return node;
            } catch (IOException ignored) {
                // Do not log payloads: response.created can echo prompts, images and other input.
            }
            throw failure("AI 响应格式无效（未重发）");
        }

        StringBuilder part(int output, int content) {
            return parts.computeIfAbsent(output, k -> new java.util.TreeMap<>())
                    .computeIfAbsent(content, k -> new StringBuilder());
        }

        void replacePart(int output, int content, String text) {
            StringBuilder target = part(output, content);
            target.setLength(0);
            target.append(text);
        }

        void accept(JsonNode node, String eventName) {
            usage.read(node.path("usage"));
            JsonNode response = node.path("response");
            usage.read(response.path("usage"));
            String type = node.path("type").asText(eventName);
            lastEvent = type.isBlank() ? (node.has("choices") ? "chat.completion" : "json") : diagnosticToken(type);
            String status = (response.isObject() ? response : node).path("status").asText("");
            if ("response.incomplete".equals(type) || "incomplete".equals(status)) {
                String reason = (response.isObject() ? response : node).path("incomplete_details").path("reason").asText("");
                throw failure("max_output_tokens".equals(reason) ? "AI 输出达到长度上限，内容未完成" : "AI 响应未完成");
            }
            if ("error".equals(type) || "response.failed".equals(type) || "response.error".equals(type)
                    || "failed".equals(status) || "cancelled".equals(status)
                    || node.path("error").isObject() || response.path("error").isObject()) {
                throw failure("AI 流式响应失败（上游返回错误，未重发）");
            }
            int output = node.path("output_index").asInt(0);
            int content = node.path("content_index").asInt(0);
            if ("response.output_text.delta".equals(type)) {
                part(output, content).append(node.path("delta").asText(""));
            } else if ("response.output_text.done".equals(type)) {
                replacePart(output, content, node.path("text").asText(""));
            } else if ("response.content_part.done".equals(type) && "output_text".equals(node.path("part").path("type").asText())) {
                replacePart(output, content, node.path("part").path("text").asText(""));
            } else if ("response.output_item.done".equals(type) && node.path("item").path("content").isArray()) {
                parts.remove(output);
                int index = 0;
                for (JsonNode block : node.path("item").path("content")) {
                    if ("output_text".equals(block.path("type").asText())) replacePart(output, index, block.path("text").asText(""));
                    index++;
                }
            } else if ("response.completed".equals(type)) {
                if (response.has("output") || response.has("output_text")) {
                    parts.clear();
                    replacePart(0, 0, responseText(response));
                }
                completed = terminal = true;
            } else if (node.path("choices").isArray()) {
                for (JsonNode choice : node.path("choices")) {
                    if (choice.path("index").asInt(0) != 0) continue;
                    String reason = choice.path("finish_reason").asText("");
                    if ("length".equals(reason)) throw failure("AI 输出达到长度上限，内容未完成");
                    if ("content_filter".equals(reason)) throw failure("AI 响应未完成（内容过滤）");
                    if (!reason.isBlank()) completed = true;
                    String delta = extractMessageText(choice.path("delta").path("content"));
                    if (!delta.isEmpty()) part(0, 0).append(delta);
                    String snapshot = extractMessageText(choice.path("message").path("content"));
                    if (!snapshot.isBlank()) replacePart(0, 0, snapshot);
                }
            } else if (type.isEmpty() && (node.has("output") || node.has("output_text"))) {
                replacePart(0, 0, responseText(node));
                completed = status.isBlank() || "completed".equals(status);
            }
        }

        String result() {
            dispatch();
            if (!sse) {
                if (raw.isEmpty()) throw failure("AI 返回了空响应（未重发）");
                JsonNode node = parse(raw.toString());
                accept(node, "");
                // Legacy non-streaming chat responses may omit finish_reason.
                if (node.path("choices").isArray() && node.path("status").asText("").isBlank()) completed = true;
            }
            if (!completed) throw failure("AI 流式响应中断（未收到完成事件，未重发）");
            StringBuilder text = new StringBuilder();
            parts.values().forEach(blocks -> blocks.values().forEach(text::append));
            if (text.toString().isBlank()) throw failure("AI 返回里没有文本内容（未重发）");
            return text.toString().trim();
        }

        AiCallException failure(String message) {
            return new AiCallException(message, false, false, null);
        }

        int textLength() {
            return parts.values().stream().flatMap(blocks -> blocks.values().stream()).mapToInt(StringBuilder::length).sum();
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

    private String responseText(JsonNode root) {
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
        return "";
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
        // Gateways can return whole HTML pages or echo sensitive input in error.message.
        // Expose only a bounded code/type; the HTTP status is always included by the caller.
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String code = root.path("error").path("code").asText("");
            if (code.isBlank()) code = root.path("error").path("type").asText("");
            return code.isBlank() ? "上游返回错误" : diagnosticToken(code);
        } catch (Exception e) {
            return "上游返回非 JSON 错误页";
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
