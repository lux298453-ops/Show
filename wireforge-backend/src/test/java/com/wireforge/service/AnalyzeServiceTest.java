package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testParseJsonWithTruncatedElements() {
        // Simulate AnalyzeService.parseJson logic
        AnalyzeService service = new AnalyzeService(
                null, null, null, null, null, null,
                objectMapper, null, null, null, null, null, null
        );

        String truncatedRaw = """
                ```json
                {
                  "page_name": "测试页面",
                  "elements": [
                    {
                      "type": "button",
                      "label": "确认}",
                      "bbox": [10, 20, 100, 40]
                    },
                    {
                      "type": "icon",
                      "label": "设置",
                      "bbox": [50, 60, 24, 24]
                    },
                    {
                      "type": "text",
                      "label": "正在加载未完成的半截
                """;

        JsonNode root = service.parseJson(truncatedRaw);
        assertNotNull(root);
        assertEquals("测试页面", root.path("page_name").asText());
        JsonNode elements = root.path("elements");
        assertTrue(elements.isArray());
        assertEquals(2, elements.size());
        assertEquals("button", elements.get(0).path("type").asText());
        assertEquals("确认}", elements.get(0).path("label").asText());
        assertEquals("icon", elements.get(1).path("type").asText());
    }

    @Test
    public void testParseJsonNormal() {
        AnalyzeService service = new AnalyzeService(
                null, null, null, null, null, null,
                objectMapper, null, null, null, null, null, null
        );

        String normalRaw = """
                {
                  "page_name": "首页",
                  "elements": [
                    {
                      "type": "button",
                      "label": "开始",
                      "bbox": [0, 0, 100, 50]
                    }
                  ]
                }
                """;
        JsonNode root = service.parseJson(normalRaw);
        assertNotNull(root);
        assertEquals("首页", root.path("page_name").asText());
        assertEquals(1, root.path("elements").size());
    }
}
