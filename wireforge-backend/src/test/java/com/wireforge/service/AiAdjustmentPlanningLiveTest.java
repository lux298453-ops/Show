package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.Page;
import com.wireforge.mapper.PageMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in: one real HTML-model call, mocked image provider, no database or project writes. */
@EnabledIfSystemProperty(named = "wireforge.live-adjustment-plan", matches = "true")
class AiAdjustmentPlanningLiveTest {
    @Test void plansTheReportedNoticeMoveAndBackgroundWithoutEchoingOrMixingSvgIdentifiers() throws Exception {
        Path sourceFile = Path.of(System.getProperty("wireforge.adjustment-source"));
        Path output = Path.of(System.getProperty("wireforge.adjustment-output"));
        Files.createDirectories(output);
        Map<?, ?> config = new Yaml().load(Files.readString(Path.of("config/application.yml")));
        Map<?, ?> ai = (Map<?, ?>) ((Map<?, ?>) config.get("wireforge")).get("ai");
        AiClient client = new AiClient(value(ai, "base-url", ""), value(ai, "api-key", ""), value(ai, "model", ""),
                value(ai, "html-model", ""), Integer.parseInt(value(ai, "max-output-tokens", "16000")), 300);
        ReflectionTestUtils.setField(client, "protocol", value(ai, "protocol", "responses"));
        ReflectionTestUtils.setField(client, "htmlReasoningEffort", value(ai, "html-reasoning-effort", "high"));
        String original = Files.readString(sourceFile);
        ObjectMapper json = new ObjectMapper();
        PageMapper pages = mock(PageMapper.class);
        Page page = new Page(); page.setId(2503L); page.setProjectId(182L); page.setName("宠物家园首页");
        page.setHtmlContent(original); page.setCanvasWidth(375); page.setCanvasHeight(833);
        when(pages.selectById(2503L)).thenReturn(page);
        AiClient recordingClient = mock(AiClient.class);
        AtomicReference<String> raw = new AtomicReference<>();
        when(recordingClient.generateHtmlText(anyString(), anyString())).thenAnswer(call -> {
            String response = client.generateHtmlText(call.getArgument(0), call.getArgument(1));
            raw.set(response); Files.writeString(output.resolve("live-model-response.json"), response);
            return response;
        });
        ProjectArtworkService artwork = mock(ProjectArtworkService.class);
        when(artwork.generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ProjectArtworkService.Artwork("mocked-image", "背景", "仅验证出图计划", "/api/assets/artwork/182/" + "a".repeat(64) + ".png", "background"));
        AiAdjustService service = new AiAdjustService(pages, recordingClient, json, new AiAdjustmentArtworkService(artwork));
        var result = service.adjustPage(182L, 2503L,
                "现在的获得1个新装扮有点挡住扭蛋机图标了，往下一点挪到其他收集的图标那里，然后重新生成背景图", original, "", "live-patch-check");
        var response = json.readTree(raw.get().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
        assertTrue(response.path("edits").isArray(), "Simple movement must use local edits instead of echoing the whole document");
        assertTrue(response.path("modified_html").asText().isBlank());
        var before = Jsoup.parse(original); var after = Jsoup.parse(result.getModifiedHtml());
        assertEquals(before.select("svg").outerHtml(), after.select("svg").outerHtml());
        assertEquals(before.select("script").outerHtml(), after.select("script").outerHtml());
        assertEquals(before.select("[data-nav]").eachAttr("data-nav"), after.select("[data-nav]").eachAttr("data-nav"));
        assertEquals(1, result.getGeneratedImages().size());
        assertTrue(result.isChanged());
        assertNotEquals(before.selectFirst("[data-wf-id=new-outfit-tip]").attr("style"), after.selectFirst("[data-wf-id=new-outfit-tip]").attr("style"));
        verify(pages, never()).updateById(any());
        verify(recordingClient, times(1)).generateHtmlText(anyString(), anyString());
        Files.writeString(output.resolve("live-result.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "realHtmlModel", true, "mockedImageProvider", true, "projectWritten", false,
                "inputHtmlLength", original.length(), "outputLength", raw.get().length(), "editCount", response.path("edits").size(),
                "preservedSvgCount", before.select("svg").size(), "generatedImageTasks", result.getGeneratedImages().size())));
    }

    private static String value(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key); return value == null ? fallback : value.toString();
    }
}
