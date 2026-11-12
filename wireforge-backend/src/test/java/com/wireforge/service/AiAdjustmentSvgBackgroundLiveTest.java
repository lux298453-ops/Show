package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.Page;
import com.wireforge.mapper.PageMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in: the reported page with real HTML/image providers; produces a reviewable draft, never saves a page. */
@EnabledIfSystemProperty(named = "wireforge.live-svg-background", matches = "true")
class AiAdjustmentSvgBackgroundLiveTest {
    @Test void movesTheReportedNoticeAndGeneratesAnImageInsideTheActualSvgBackground() throws Exception {
        Path output = Path.of(System.getProperty("wireforge.adjustment-output"));
        Files.createDirectories(output);
        String source = Files.readString(Path.of(System.getProperty("wireforge.adjustment-source")));
        var config = new YamlPropertySourceLoader().load("local", new FileSystemResource("config/application.yml")).get(0);
        String baseUrl = value(config, "wireforge.ai.base-url", ""), apiKey = value(config, "wireforge.ai.api-key", "");
        AiClient client = new AiClient(baseUrl, apiKey, value(config, "wireforge.ai.model", ""),
                value(config, "wireforge.ai.html-model", ""), Integer.parseInt(value(config, "wireforge.ai.max-output-tokens", "16000")), 300);
        ReflectionTestUtils.setField(client, "protocol", value(config, "wireforge.ai.protocol", "responses"));
        ReflectionTestUtils.setField(client, "htmlReasoningEffort", value(config, "wireforge.ai.html-reasoning-effort", "high"));
        ProjectArtworkService artwork = new ProjectArtworkService(System.getProperty("wireforge.adjustment-designs"),
                value(config, "wireforge.ai.artwork.base-url", baseUrl), value(config, "wireforge.ai.artwork.api-key", apiKey),
                value(config, "wireforge.ai.artwork.model", "gpt-image-2"),
                Boolean.parseBoolean(value(config, "wireforge.ai.artwork.enabled", "true")),
                Integer.parseInt(value(config, "wireforge.ai.artwork.timeout-seconds", "300")), 1);
        ObjectMapper json = new ObjectMapper();
        var before = Jsoup.parse(source);
        AiClient recordingClient = mock(AiClient.class);
        when(recordingClient.generateHtmlText(anyString(), anyString())).thenAnswer(call -> {
            String raw = client.generateHtmlText(call.getArgument(0), call.getArgument(1));
            Files.writeString(output.resolve("live-model-response.json"), raw);
            var plan = json.readTree(raw.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
            assertTrue(plan.path("edits").isArray());
            assertTrue(plan.path("modified_html").asText().isBlank());
            assertEquals(1, plan.path("image_tasks").size());
            var task = plan.path("image_tasks").get(0);
            List<Element> targets = before.getAllElements().stream().filter(node -> task.path("target_id").asText().equals(node.attr(task.path("target_attribute").asText()))).toList();
            assertEquals(1, targets.size());
            assertEquals("sunlit-room-illustration", targets.get(0).attr("data-wf-id"), "Use the actual background, not a foreground container");
            return raw;
        });
        PageMapper pages = mock(PageMapper.class);
        Page page = new Page(); page.setId(2464L); page.setProjectId(180L); page.setName("宠物家园首页");
        page.setHtmlContent(source); page.setCanvasWidth(375); page.setCanvasHeight(833);
        when(pages.selectById(2464L)).thenReturn(page);
        AiAdjustService service = new AiAdjustService(pages, recordingClient, json, new AiAdjustmentArtworkService(artwork));
        var result = service.adjustPage(180L, 2464L,
                "现在的获得1个新装版 有点挡住扭蛋机图标了，往下一点挪到其他收集的图标那里，然后重新生成背景图", source, "", "svg-background-verification-20261110");
        var after = Jsoup.parse(result.getModifiedHtml());
        Element original = before.selectFirst("[data-wf-id=sunlit-room-illustration]");
        Element changed = after.selectFirst("[data-wf-id=sunlit-room-illustration]");
        assertEquals(original.attributes(), changed.attributes());
        Element image = changed.selectFirst("image[data-wf-adjustment-image=true]");
        assertNotNull(image); assertEquals(result.getGeneratedImages().get(0).url(), image.attr("href"));
        for (Element svg : before.select("svg")) {
            if (svg == original) continue;
            String uid = svg.attr("data-wf-uid");
            assertFalse(uid.isBlank());
            assertEquals(svg.outerHtml(), after.getElementsByAttributeValue("data-wf-uid", uid).first().outerHtml());
        }
        assertEquals(before.select("button").outerHtml(), after.select("button").outerHtml());
        assertEquals(before.select("script").outerHtml(), after.select("script").outerHtml());
        assertNotEquals(before.selectFirst("[data-wf-id=new-outfit-tip]").attr("style"), after.selectFirst("[data-wf-id=new-outfit-tip]").attr("style"));
        assertTrue(result.isChanged()); assertEquals(1, result.getGeneratedImages().size());
        String digest = image.attr("href").replaceAll(".*/", "").replace(".png", "");
        Path imageFile = artwork.image(180, digest).getFile().toPath();
        var bitmap = ImageIO.read(imageFile.toFile()); assertNotNull(bitmap); assertTrue(bitmap.getWidth() >= 256);
        Files.copy(imageFile, output.resolve("background-generated.png"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(output.resolve("page-2464-after.html"), result.getModifiedHtml());
        Files.writeString(output.resolve("live-adjustment-response.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        Files.writeString(output.resolve("live-result.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "realHtmlModel", true, "realImageProvider", true, "projectWritten", false, "projectId", 180, "pageId", 2464,
                "inputHtmlLength", source.length(), "preservedOtherSvgCount", before.select("svg").size() - 1,
                "generatedImageUrl", image.attr("href"), "imageWidth", bitmap.getWidth(), "imageHeight", bitmap.getHeight())));
        verify(pages, never()).updateById(any());
        verify(recordingClient, times(1)).generateHtmlText(anyString(), anyString());
    }

    private static String value(PropertySource<?> config, String name, String fallback) {
        Object value = config.getProperty(name); return value == null ? fallback : value.toString();
    }
}
