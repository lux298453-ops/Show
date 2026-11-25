package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateHtmlRendererRecognizedLayoutTest {
    @ParameterizedTest
    @CsvSource({
            "普通页面, design.png",
            "普通页面, 11.png",
            "普通页面, folder-524323/design.png",
            "普通页面, 195.png",
            "普通页面, 12455.png",
            "普通页面, 187.png",
            "普通页面, 背包@3x.png",
            "扭蛋列表, design.png",
            "更换耀宝, design.png",
            "抽奖结果列表, design.png"
    })
    void recognizesTheSameFramesRegardlessOfLegacyDesignNames(String name, String filename) {
        Page page = new Page();
        page.setId(1L);
        page.setName(name);
        page.setPageType(PageTypes.PAGE);
        page.setCanvasWidth(375);
        page.setCanvasHeight(833);
        page.setLayoutNorm(0);
        page.setBackgroundImage("fixture-drafts/" + filename);
        List<Element> elements = List.of(
                element(1, "container", "展示区", 20, 100, 160, 180),
                element(2, "container", "货架", 40, 320, 280, 150),
                element(3, "container", "底部选项", 20, 690, 150, 50),
                element(4, "button", "一键入住", 230, 696, 80, 24),
                element(5, "image", "示例图片", 315, 630, 38, 40));

        var renderer = new TemplateHtmlRenderer(new DesignColorSampler(), new ObjectMapper());
        var bundle = new TemplateHtmlRenderer.PageBundle(page, elements, Map.of());
        var document = Jsoup.parse(renderer.render(page, Map.of(1L, bundle), Map.of(1L, name), "测试原型", null));

        for (Element element : elements) {
            var node = document.selectFirst("[data-wf-element-id=" + element.getId() + "]");
            assertNotNull(node, "Missing editable element " + element.getId());
            String frame = "left:%.0fpx;top:%.0fpx;width:%.0fpx;height:%.0fpx;".formatted(
                    element.getPositionX(), element.getPositionY(), element.getWidth(), element.getHeight());
            assertTrue(node.attr("style").startsWith(frame), () -> node.outerHtml());
        }
    }

    private Element element(long id, String type, String label, double x, double y, double width, double height) {
        Element element = new Element();
        element.setId(id);
        element.setPageId(1L);
        element.setType(type);
        element.setLabel(label);
        element.setPositionX(x);
        element.setPositionY(y);
        element.setWidth(width);
        element.setHeight(height);
        return element;
    }
}
