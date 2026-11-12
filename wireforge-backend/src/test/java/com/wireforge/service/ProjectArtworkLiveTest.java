package com.wireforge.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in integration check: one text-only image request, cached in the selected local project. No page/DB writes. */
@EnabledIfSystemProperty(named = "wireforge.live-artwork", matches = "true")
class ProjectArtworkLiveTest {
    @Test void configuredImageProviderProducesAPersistentCharacterAsset() throws Exception {
        var config = new YamlPropertySourceLoader().load("local", new FileSystemResource("config/application.yml")).get(0);
        long projectId = Long.parseLong(System.getProperty("wireforge.live-artwork-project"));
        String designs = System.getProperty("wireforge.live-artwork-designs");
        var service = new ProjectArtworkService(designs, (String)config.getProperty("wireforge.ai.base-url"),
                (String)config.getProperty("wireforge.ai.api-key"), "gpt-image-2", true, 180, 1);
        String html = """
                <html><head></head><body><div style="width:300px;height:400px" data-wf-uid="portrait-youxiao"
                data-wf-artwork-key="幽筱-黑金战甲" data-wf-artwork-name="幽筱 · 黑金战甲"
                data-wf-artwork-prompt="精细东方奇幻游戏成年女性战士角色立绘，银白色长发高束，头发有少量暖红色发束与金色弯角发饰，
                自信冷静的面容，斜侧身回眸姿态，黑色与香槟金层叠的雕花金属肩甲和胸甲，少量红色流苏，
                金属精细花纹与柔和高光，完整头部和上半身，暖金浅背景，角色居中占画面85%，精致商业游戏原画质感。
                只画角色插图，不画文字、品牌、价格、按钮和卡片边框。"><svg><circle r="20"/></svg></div></body></html>
                """;
        var allowed = new HashSet<String>(); String result = service.resolve(projectId, html, allowed, true);
        var image = Jsoup.parse(result).selectFirst("img[data-wf-artwork-image]");
        assertNotNull(image, "当前图片接口没有生成可用图片，请检查素材日志");
        String digest = image.attr("src").replaceAll(".*/", "").replace(".png", "");
        assertTrue(service.image(projectId, digest).exists());
        Path output = Path.of(System.getProperty("wireforge.live-artwork-output")); Files.createDirectories(output);
        Files.copy(service.image(projectId, digest).getFile().toPath(), output.resolve("character-youxiao.png"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(output.resolve("character-youxiao.html"), result);
    }
}
