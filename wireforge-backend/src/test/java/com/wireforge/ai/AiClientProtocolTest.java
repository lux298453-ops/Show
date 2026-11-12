package com.wireforge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class AiClientProtocolTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CopyOnWriteArrayList<String> paths = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<JsonNode> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private int status = 200;
    private long delayMillis;
    private String responseBody;
    private String responseContentType = "text/event-stream";
    private boolean rejectStreaming;
    private boolean rejectReasoning;
    private long holdOpenMillis;
    private boolean truncateTransport;

    @BeforeEach void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            JsonNode request = json.readTree(exchange.getRequestBody());
            requests.add(request);
            if (delayMillis > 0) {
                try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
            boolean reasoningPresent = rejectReasoning && (request.has("reasoning") || request.has("reasoning_effort"));
            int responseStatus = (rejectStreaming && request.path("stream").asBoolean()) || reasoningPresent ? 400 : status;
            String result = reasoningPresent ? "{\"error\":{\"message\":\"Unsupported parameter: reasoning\"}}"
                    : responseStatus == 400 && rejectStreaming
                    ? "{\"error\":{\"message\":\"stream is not supported\"}}"
                    : responseBody != null ? responseBody : status == 200
                    ? path.endsWith("/responses")
                        ? "data: {\"type\":\"response.output_text.delta\",\"delta\":\"ok\"}\n\ndata: [DONE]\n\n"
                        : "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n"
                    : "{\"error\":{\"message\":\"Invalid API key\"}}";
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", responseStatus == 200 ? responseContentType : "application/json");
            exchange.sendResponseHeaders(responseStatus, holdOpenMillis > 0 ? 0 : bytes.length + (truncateTransport ? 100 : 0));
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
                out.flush();
                if (holdOpenMillis > 0) {
                    try { Thread.sleep(holdOpenMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    private AiClient client() {
        return new AiClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-only", "existing-model", "existing-html-model", 256, 5);
    }

    @Test void responsesPreferenceSendsImageAndHtmlModelThroughResponses() {
        AiClient client = client();
        ReflectionTestUtils.setField(client, "protocol", "responses");
        assertEquals("ok", client.generateHtmlWithImage("system", "user", "image/png", new byte[]{1, 2, 3}));
        assertEquals(java.util.List.of("/v1/responses"), paths);
        JsonNode request = requests.get(0);
        assertEquals("existing-html-model", request.path("model").asText());
        assertEquals("system", request.path("instructions").asText());
        assertEquals("input_image", request.path("input").get(0).path("content").get(1).path("type").asText());
        assertTrue(request.path("stream").asBoolean());
        assertFalse(request.has("messages"));
        assertEquals("high", request.path("reasoning").path("effort").asText());
    }

    @Test void defaultStillUsesChatCompletionsForExistingConfigurations() {
        assertEquals("ok", client().generateText("system", "user"));
        assertEquals(java.util.List.of("/v1/chat/completions"), paths);
        assertEquals("existing-model", requests.get(0).path("model").asText());
        assertTrue(requests.get(0).has("messages"));
        assertEquals("low", requests.get(0).path("reasoning_effort").asText());
    }

    @Test void htmlRequestsKeepSmallPortraitDetailsInBothSingleAndMultiImageCalls() throws Exception {
        var source = new java.awt.image.BufferedImage(1080, 2400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(source, "png", bytes);
        AiClient client = client(); ReflectionTestUtils.setField(client, "protocol", "responses");
        client.generateHtmlWithImage("system", "user", "image/png", bytes.toByteArray());
        client.generateHtmlWithImages("system", "user", java.util.List.of(new AiClient.ImageInput("image/png", bytes.toByteArray())));
        for (var request : requests) {
            String data = request.path("input").get(0).path("content").get(1).path("image_url").asText();
            var sent = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1))));
            assertEquals(1080, sent.getWidth()); assertEquals(2400, sent.getHeight());
        }
        assertEquals(1080, AiClient.prepareImage(bytes.toByteArray(), "image/png").height());
    }

    @Test void authenticationFailureDoesNotRetryOrSwitchProtocols() {
        status = 401;
        AiClient client = client();
        ReflectionTestUtils.setField(client, "protocol", "responses");
        RuntimeException failure = assertThrows(RuntimeException.class, () -> client.generateText("system", "user"));
        assertTrue(failure.getMessage().contains("401"));
        assertEquals(java.util.List.of("/v1/responses"), paths);
    }

    @Test void gatewayErrorIsNotAHandshakeFailureAndDoesNotRetryOrDowngrade() {
        status = 502;
        responseBody = "<html>private input reasoning unsupported stream unsupported</html>";
        AiClient client = client();
        ReflectionTestUtils.setField(client, "protocol", "responses");
        var failure = assertThrows(RuntimeException.class, () -> client.generateHtmlText("system", "user"));
        assertTrue(failure.getMessage().contains("502"));
        assertFalse(failure.getMessage().contains("private input"));
        assertFalse(failure.getMessage().contains("未重复计费"));
        assertEquals(java.util.List.of("/v1/responses"), paths);
        assertEquals("high", requests.get(0).path("reasoning").path("effort").asText());
    }

    @Test void rateLimitAndServiceUnavailableDoNotAutomaticallyResubmitPost() {
        for (int code : new int[]{429, 503}) {
            status = code;
            responseBody = "{\"error\":{\"code\":\"upstream_unavailable\",\"message\":\"private input\"}}";
            var failure = assertThrows(RuntimeException.class, () -> client().generateText("system", "user"));
            assertTrue(failure.getMessage().contains(String.valueOf(code)));
            assertFalse(failure.getMessage().contains("private input"));
        }
        assertEquals(2, paths.size());
    }

    @Test void onlyExplicitConnectionFailuresAreEligibleForRetry() {
        assertFalse(AiClient.ioError(new java.io.IOException("header parser received no bytes")).retryable);
        assertFalse(AiClient.ioError(new java.net.SocketException("Connection reset")).retryable);
        assertFalse(AiClient.ioError(new java.net.http.HttpTimeoutException("request timed out")).retryable);
        assertTrue(AiClient.ioError(new java.net.ConnectException("Connection refused")).retryable);
        assertTrue(AiClient.ioError(new java.net.UnknownHostException("test.invalid")).retryable);
        assertTrue(AiClient.ioError(new java.net.http.HttpConnectTimeoutException("connect timed out")).retryable);
    }

    @Test void explicitUnsupportedReasoningParameterCanBeRemovedOnce() {
        rejectReasoning = true;
        AiClient client = client();
        ReflectionTestUtils.setField(client, "protocol", "responses");
        assertEquals("ok", client.generateHtmlText("system", "user"));
        assertEquals(2, requests.size());
        assertEquals("high", requests.get(0).path("reasoning").path("effort").asText());
        assertFalse(requests.get(1).has("reasoning"));
    }

    @Test void mentioningReasoningInGenericBadRequestDoesNotTriggerResubmission() {
        status = 400;
        responseBody = "{\"error\":{\"message\":\"reasoning quota exhausted\"}}";
        assertThrows(RuntimeException.class, () -> client().generateHtmlText("system", "user"));
        assertEquals(1, paths.size());
    }

    @Test void longHtmlCanFinishWhileOrdinaryRequestsKeepTheirShorterTimeout() {
        delayMillis = 1500;
        AiClient client = new AiClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-only", "existing-model", "existing-html-model", 256, 1);
        ReflectionTestUtils.setField(client, "htmlTimeoutSeconds", 3);
        assertEquals("ok", client.generateHtmlWithImage("system", "user", "image/png", new byte[]{1}));
        assertThrows(RuntimeException.class, () -> client.generateText("system", "user"));
        assertEquals(2, paths.size(), "Timed out calls must not be repeated");
    }

    private String completed(String text) throws Exception {
        String escaped = json.writeValueAsString(text);
        return "event: response.completed\r\ndata: {\"type\":\"response.completed\",\r\n"
                + "data: \"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"message\","
                + "\"content\":[{\"type\":\"output_text\",\"text\":" + escaped + "}]}],"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}}\r\n\r\n";
    }

    @Test void heartbeatStreamWithOnlyFinalSnapshotReturnsHtmlInsteadOfRawEvents() throws Exception {
        String html = "<!doctype html><html><body>宠物家园</body></html>";
        responseBody = "\uFEFF:\r\n\r\n: keepalive\r\n\r\nevent: response.created\r\n"
                + "data: {\"type\":\"response.created\",\"response\":{\"status\":\"in_progress\"}}\r\n\r\n"
                + completed(html);
        assertEquals(html, client().generateHtmlText("system", "user"));
        assertEquals(1, paths.size());
    }

    @Test void finalSnapshotReplacesPartialDeltasAndDoneCopiesWithoutDuplicatingHtml() throws Exception {
        responseBody = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"<html>\"}\n\n"
                + "data: {\"type\":\"response.output_text.done\",\"text\":\"<html>complete</html>\"}\n\n"
                + "data: {\"type\":\"response.output_item.done\",\"item\":{\"content\":[{\"type\":\"output_text\",\"text\":\"<html>complete</html>\"}]}}\n\n"
                + completed("<html>complete</html>");
        assertEquals("<html>complete</html>", client().generateHtmlText("system", "user"));
    }

    @Test void doneOnlyTextAndContentPartsRemainAvailableWhenFinalEventOmitsSnapshot() {
        responseBody = "event: response.output_text.done\ndata: {\"output_index\":0,\"content_index\":0,\"text\":\"<html>\"}\n\n"
                + "data: {\"type\":\"response.content_part.done\",\"output_index\":0,\"content_index\":1,\"part\":{\"type\":\"output_text\",\"text\":\"complete</html>\"}}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n";
        assertEquals("<html>complete</html>", client().generateHtmlText("system", "user"));
    }

    @Test void normalJsonResponseIsStillAcceptedWhenStreamWasRequested() {
        responseContentType = "application/json";
        responseBody = "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"complete\"}]}]}";
        assertEquals("complete", client().generateText("system", "user"));
        assertEquals(1, paths.size());
    }

    @Test void explicitlyRejectedStreamingCanReturnSseToTheSingleNonStreamingRequest() throws Exception {
        rejectStreaming = true;
        responseBody = completed("complete");
        assertEquals("complete", client().generateText("system", "user"));
        assertEquals(2, paths.size());
        assertTrue(requests.get(0).path("stream").asBoolean());
        assertFalse(requests.get(1).path("stream").asBoolean());
    }

    @Test void partialTextWithoutCompletionIsNotReturnedOrResubmitted() {
        responseBody = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"<html>partial\"}\n\n";
        var error = assertThrows(RuntimeException.class, () -> client().generateHtmlText("system", "user"));
        assertTrue(error.getMessage().contains("未收到完成事件"));
        assertEquals(1, paths.size());
    }

    @Test void brokenHttpBodyDoesNotResubmitOrReturnPartialText() {
        truncateTransport = true;
        responseBody = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\n";
        var error = assertThrows(RuntimeException.class, () -> client().generateHtmlText("system", "user"));
        assertTrue(error.getMessage().contains("连接读取失败"));
        assertEquals(1, paths.size());
    }

    @Test void createdOnlyStreamDoesNotBecomeHtmlOrTriggerAnotherBilledRequest(CapturedOutput output) {
        responseBody = ":\n\nevent: response.created\ndata: {\"type\":\"response.created\",\"response\":{\"status\":\"in_progress\",\"instructions\":\"private input\"}}\n\n";
        var error = assertThrows(RuntimeException.class, () -> client().generateHtmlText("system", "user"));
        assertTrue(error.getMessage().contains("未收到完成事件"));
        assertFalse(error.getMessage().contains("private input"));
        assertEquals(1, paths.size());
        assertTrue(output.getOut().contains("[AI响应失败]"));
        assertTrue(output.getOut().contains("HTTP=200"));
        assertTrue(output.getOut().contains("事件数=1 正文字符=0 最后事件=response.created 完成=false"));
        assertFalse(output.getOut().contains("private input"));
    }

    @Test void incompleteResponseRejectsEvenApparentlyCompleteHtmlWithoutRetrying() {
        responseBody = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"<html></html>\"}\n\n"
                + "data: {\"type\":\"response.incomplete\",\"response\":{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}}\n\n";
        var error = assertThrows(RuntimeException.class, () -> client().generateHtmlText("system", "user"));
        assertTrue(error.getMessage().contains("长度上限"));
        assertEquals(1, paths.size());
    }

    @Test void failedResponseAndErrorEventAreNeverReturnedAsContent() {
        responseBody = "event: error\ndata: {\"code\":\"server_error\",\"message\":\"private server details\"}\n\n";
        var error = assertThrows(RuntimeException.class, () -> client().generateText("system", "user"));
        assertTrue(error.getMessage().contains("上游返回错误"));
        assertFalse(error.getMessage().contains("private server details"));
        assertEquals(1, paths.size());
    }

    @Test void malformedDataIsNotSilentlyDroppedOrLeaked() {
        responseBody = "data: {invalid private input}\n\ndata: [DONE]\n\n";
        var error = assertThrows(RuntimeException.class, () -> client().generateText("system", "user"));
        assertTrue(error.getMessage().contains("格式无效"));
        assertFalse(error.getMessage().contains("private input"));
        assertEquals(1, paths.size());
    }

    @Test void chatLengthFinishReasonIsFailureRatherThanACompletedPrototype() {
        responseBody = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n";
        var error = assertThrows(RuntimeException.class, () -> client().generateHtmlText("system", "user"));
        assertTrue(error.getMessage().contains("长度上限"));
        assertEquals(1, paths.size());
    }

    @Test void completionEventFinishesWithoutWaitingForRelayToCloseTheConnection() throws Exception {
        responseBody = completed("complete");
        holdOpenMillis = 1800;
        AiClient client = new AiClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "test-only", "existing-model", "existing-html-model", 256, 1);
        assertEquals("complete", client.generateText("system", "user"));
        assertEquals(1, paths.size());
    }
}
