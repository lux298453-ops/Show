package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageNameResolverTest {
    static Page scan(long id, String source) {
        Page page = new Page(); page.setId(id); page.setName(source); page.setSourceName(source);
        page.setNameSource("scan"); page.setAnalyzed(0); return page;
    }

    @Test void exportNumbersAndGenericNamesHaveNoBusinessMappings() {
        for (String source : List.of("2455", "87", "887", "524323", "95", "image_123", "IMG_0001", "Frame 42")) {
            var name = PageNameResolver.resolve(scan(1, source), "账户资料", List.of());
            assertEquals("账户资料", name.name(), source); assertEquals("ai", name.source());
        }
    }

    @Test void cleansAllSupportedExtensionsAndRetainsFolderGroupsAndVariants() {
        for (String extension : List.of(".png", ".PNG", ".jpg", ".JPEG", ".webp")) {
            var name = PageNameResolver.resolve(scan(1, "A 版/ 商城@3x-2" + extension), "另一个名字", List.of());
            assertEquals("A 版/商城-2", name.name()); assertEquals("filename", name.source());
        }
        assertEquals("B版/确认购买", PageNameResolver.resolve(scan(2, "B版/image_42@2x.webp"), "确认购买", List.of()).name());
        assertEquals("背包_2", PageNameResolver.resolve(scan(1, "背包_2"), "道具购买确认弹窗", List.of()).name());
    }

    @Test void editorWordsAndBusinessLabelsAreMeaningfulFilenames() {
        for (String name : List.of("方案", "编辑", "晚上室内", "组队", "image-gallery", "功能设置-2"))
            assertEquals(name, PageNameResolver.resolve(scan(1, name), "被忽略的建议", List.of()).name());
    }

    @Test void stableAndUserNamesSurviveReanalysis() {
        for (String source : List.of("ai", "filename", "user", "legacy")) {
            Page page = scan(1, "image_1"); page.setName("用户熟悉的名称"); page.setNameSource(source);
            assertEquals("用户熟悉的名称", PageNameResolver.resolve(page, "新建议", List.of()).name());
        }
        Page legacy = scan(1, "旧名称"); legacy.setNameSource(null); legacy.setAnalyzed(1);
        assertEquals("旧名称", PageNameResolver.resolve(legacy, "新建议", List.of()).name());
    }

    @Test void genericFallbackRemainsRetryableAndOnlyActualCollisionsNeedSuffixes() {
        assertEquals("scan", PageNameResolver.resolve(scan(1, "image_1"), "", List.of()).source());
        Page a = scan(1, "A/商城"), b = scan(2, "B/商城");
        assertEquals("A/商城", PageNameResolver.resolve(a, "", List.of(b)).name());
        Page collision = scan(3, "A/商城");
        assertEquals("A/商城（#3）", PageNameResolver.resolve(collision, "", List.of(a)).name());
    }

    @Test void ocrTextDoesNotRenameAFileOrClassifyItsSuffix() throws Exception {
        var elements = new ObjectMapper().readTree("[{\"label\":\"购买 钻石 道具不足\"}]");
        assertEquals("背包-2", PageNameResolver.resolve("/designs/背包@3x-2.JPG", elements, "错误的弹窗名"));
    }
}
