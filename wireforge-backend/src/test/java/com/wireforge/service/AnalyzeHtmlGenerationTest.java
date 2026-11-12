package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.ai.HtmlRenderer;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyzeHtmlGenerationTest {
    @BeforeAll static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "html-generation-test"), Page.class);
    }
    @TempDir Path dir;
    final AiClient ai = mock(AiClient.class);
    final PageMapper pages = mock(PageMapper.class);
    final ElementMapper elements = mock(ElementMapper.class);
    final InteractionMapper interactions = mock(InteractionMapper.class);
    final AssetService assets = mock(AssetService.class);
    final HtmlRenderer renderer = mock(HtmlRenderer.class);
    final TemplateHtmlRenderer template = mock(TemplateHtmlRenderer.class);
    final AutowireRenderService bindings = mock(AutowireRenderService.class);
    final InteractionAutowireService relations = mock(InteractionAutowireService.class);
    AnalyzeService service;
    Page page, detail;

    @BeforeEach void setup() throws Exception {
        service = spy(new AnalyzeService(ai, assets, pages, elements, interactions, null, new ObjectMapper(), renderer,
                template, relations, null, null, null, bindings));
        page = new Page(); page.setId(1L); page.setProjectId(7L); page.setName("商城"); page.setCanvasWidth(375); page.setCanvasHeight(834);
        Path image = dir.resolve("shop.png"); Files.write(image, new byte[]{1,2,3}); page.setBackgroundImage(image.toString());
        detail = new Page(); detail.setId(2L); detail.setName("商品详情");
        Element buy = new Element(); buy.setId(10L); buy.setPageId(1L); buy.setType("button"); buy.setLabel("购买"); buy.setPositionX(8d); buy.setPositionY(300d); buy.setWidth(88d); buy.setHeight(30d);
        Interaction line = new Interaction(); line.setElementId(10L); line.setActionType("navigate"); line.setTargetPageId(2L);
        when(pages.selectList(any())).thenReturn(List.of(page, detail)); when(pages.selectById(1L)).thenReturn(page); when(pages.selectById(2L)).thenReturn(detail);
        when(elements.selectList(any())).thenReturn(List.of(buy)); when(elements.selectBatchIds(any())).thenReturn(List.of());
        when(interactions.selectList(any())).thenReturn(List.of(line));
        when(assets.scanUserAssetsFor(any())).thenReturn(List.of()); when(assets.getUserAssetsHtmlPrompt(any())).thenReturn("");
        doReturn("template fallback").when(service).renderTemplateHtml(any());
    }

    private String valid() { return "<!doctype html><html><head><style>body{color:#123}</style></head><body><button data-wf-element-id='10' data-nav='商品详情'>购买</button></body></html>"; }

    @Test void regeneratingHtmlSynchronizesPrototypeRelationsBeforeRefreshingPreviewBindings() {
        doReturn(valid()).when(service).renderPageHtml(page);
        service.regeneratePageHtml(1L);
        var order = inOrder(pages, relations, bindings);
        order.verify(pages).update(isNull(), any());
        order.verify(relations).autowireProjectInteractions(7L);
        order.verify(bindings).refreshProjectBindings(7L);
    }

    @Test void actualGeneratorUsesVisionHtmlAndSuppliesSourceIdsAndKnownPages() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid());
        String html = service.renderPageHtml(page);
        assertTrue(html.contains("data-wf-uid=\"wf-p1-e10\""));
        verify(ai).generateHtmlWithImage(contains("静态结构"), argThat(p -> p.contains("10 | button | 购买") && p.contains("商城") && p.contains("商品详情")), eq("image/png"), any());
        verify(service, never()).renderTemplateHtml(any());
    }

    @Test void invalidDynamicHtmlReportsFailureWithoutReplacingItWithATemplate() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid().replace("</body>", "<script>render()</script></body>"));
        assertThrows(IllegalStateException.class, () -> service.renderPageHtml(page));
        verify(service, never()).renderTemplateHtml(any());
    }

    @Test void rollbackSwitchRetainsTheLegacyRendererWithoutCallingTheModel() {
        ReflectionTestUtils.setField(service, "directHtml", false);
        assertEquals("template fallback", service.renderPageHtml(page));
        verifyNoInteractions(ai);
    }

    @Test void maintenanceKeepsTheLatestEditedHtmlEvenWhenCallerHasAnOldSnapshot() {
        Page current = new Page(); current.setId(1L); current.setHtmlContent("<html>人工编辑后的卡片</html>");
        when(pages.selectById(1L)).thenReturn(current);
        assertEquals(current.getHtmlContent(), service.preserveOrRenderHtml(page));
        verifyNoInteractions(ai); verify(service, never()).renderTemplateHtml(any());
        verify(pages, never()).update(isNull(), any());
    }

    @Test void brokenVisualRepairRetainsTheValidatedFirstVersion() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid());
        ReflectionTestUtils.setField(service, "repairEnabled", true); ReflectionTestUtils.setField(service, "repairRounds", 1);
        when(renderer.isAvailable()).thenReturn(true);
        when(renderer.inspect(anyString(), eq(375), eq(834))).thenReturn(problem());
        when(ai.generateHtmlWithImages(anyString(), anyString(), any())).thenReturn("not html");
        assertTrue(service.renderPageHtml(page).contains("wf-p1-e10"));
        verify(ai).generateHtmlWithImages(anyString(), contains("当前静态HTML"), any());
        verify(service, never()).renderTemplateHtml(any());
    }

    private HtmlRenderer.QualityReport problem() {
        return new HtmlRenderer.QualityReport(List.of("内容超出画布 [btn-buy] 操作入口被裁切"), new byte[]{1});
    }

    @Test void aCleanPageKeepsFullHtmlGenerationButDoesNotCallTheRepairModel() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid());
        ReflectionTestUtils.setField(service, "repairEnabled", true); ReflectionTestUtils.setField(service, "repairRounds", 1);
        when(renderer.isAvailable()).thenReturn(true);
        when(renderer.inspect(anyString(), eq(375), eq(834))).thenReturn(new HtmlRenderer.QualityReport(List.of(), null));
        assertTrue(service.renderPageHtml(page).contains("wf-p1-e10"));
        verify(ai, times(1)).generateHtmlWithImage(anyString(), anyString(), anyString(), any());
        verify(ai, never()).generateHtmlWithImages(anyString(), anyString(), any());
        verify(service, never()).renderTemplateHtml(any());
    }

    @Test void obviousProblemsTriggerRepairWithTheirReasonsAndStopWhenClean() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid());
        ReflectionTestUtils.setField(service, "repairEnabled", true); ReflectionTestUtils.setField(service, "repairRounds", 2);
        when(renderer.isAvailable()).thenReturn(true);
        when(renderer.inspect(anyString(), eq(375), eq(834))).thenReturn(problem(), new HtmlRenderer.QualityReport(List.of(), null));
        when(ai.generateHtmlWithImages(anyString(), anyString(), any())).thenReturn(valid().replace(">购买<", ">购买·修复<"));
        assertTrue(service.renderPageHtml(page).contains("购买·修复"));
        verify(ai, times(1)).generateHtmlWithImages(anyString(), contains("操作入口被裁切"), any());
        verify(renderer, times(2)).inspect(anyString(), eq(375), eq(834));
    }

    @Test void localInspectionFailureKeepsTheFirstHtmlAndSpendsNoRepairCall() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid());
        ReflectionTestUtils.setField(service, "repairEnabled", true); ReflectionTestUtils.setField(service, "repairRounds", 1);
        when(renderer.isAvailable()).thenReturn(true);
        when(renderer.inspect(anyString(), anyInt(), anyInt())).thenThrow(new IllegalStateException("Chromium unavailable"));
        assertTrue(service.renderPageHtml(page).contains("wf-p1-e10"));
        verify(ai, never()).generateHtmlWithImages(anyString(), anyString(), any());
    }

    @Test void disablingRepairSkipsBothInspectionAndRepairCalls() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid());
        ReflectionTestUtils.setField(service, "repairEnabled", false);
        assertTrue(service.renderPageHtml(page).contains("wf-p1-e10"));
        verifyNoInteractions(renderer);
        verify(ai, never()).generateHtmlWithImages(anyString(), anyString(), any());
    }

    @Test void aMultiImageTimeoutDoesNotResubmitTheSamePageAsASingleImage() throws Exception {
        Path reference = dir.resolve("reference.png"); Files.write(reference, new byte[]{4,5,6});
        detail.setProjectId(7L); detail.setBackgroundImage(reference.toString());
        detail.setHtmlContent("<html><head><style>body{color:#123}</style></head><body>参考风格</body></html>");
        when(ai.generateHtmlWithImages(anyString(), anyString(), any())).thenThrow(new IllegalStateException("read timeout"));
        assertThrows(IllegalStateException.class, () -> service.renderPageHtml(page));
        verify(ai, times(1)).generateHtmlWithImages(anyString(), anyString(), any());
        verify(ai, never()).generateHtmlWithImage(anyString(), anyString(), anyString(), any());
    }

    @Test void aGatewayFailureDuringRegenerationDoesNotSaveATemplateOrClaimSuccess() {
        page.setHtmlContent("<html><body>原来的角色</body></html>");
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("HTTP 502"));
        var error = assertThrows(IllegalStateException.class, () -> service.regeneratePageHtml(1L));
        assertTrue(error.getMessage().contains("502"));
        assertEquals("<html><body>原来的角色</body></html>", page.getHtmlContent());
        verify(pages, never()).update(isNull(), any()); verifyNoInteractions(bindings);
        verify(service, never()).renderTemplateHtml(any());
    }

    @Test void aDisconnectedStreamIsReportedSeparatelyFromHtmlValidationFailure() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("AI 调用失败: AI 流式响应中断（未收到完成事件，未重发）"));
        var error = assertThrows(IllegalStateException.class, () -> service.renderPageHtml(page));
        assertTrue(error.getMessage().contains("生成完成前断开连接"));
        verify(pages, never()).update(isNull(), any());
    }

    @Test void outputLimitIsReportedSeparatelyFromTimeoutOrValidationFailure() {
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("AI 输出达到长度上限，内容未完成"));
        var error = assertThrows(IllegalStateException.class, () -> service.renderPageHtml(page));
        assertTrue(error.getMessage().contains("长度上限"));
        assertFalse(error.getMessage().contains("超时"));
    }

    @Test void aRepairCannotDowngradeGeneratedArtworkToSvg() {
        ProjectArtworkService artwork = mock(ProjectArtworkService.class);
        ReflectionTestUtils.setField(service, "artworkService", artwork);
        when(artwork.assets(anyLong())).thenReturn(List.of()); when(artwork.prompt(anyLong())).thenReturn("");
        when(artwork.resolve(anyLong(), anyString(), anySet(), anyBoolean())).thenAnswer(invocation -> {
            String html = invocation.getArgument(1);
            if (Boolean.TRUE.equals(invocation.getArgument(3))) {
                ((java.util.Set<String>)invocation.getArgument(2)).add("/api/assets/artwork/7/character.png");
                return html.replace("</body>", "<img src='/api/assets/artwork/7/character.png' alt='角色'></body>");
            }
            return html;
        });
        when(ai.generateHtmlWithImage(anyString(), anyString(), anyString(), any())).thenReturn(valid());
        ReflectionTestUtils.setField(service, "repairEnabled", true); ReflectionTestUtils.setField(service, "repairRounds", 1);
        when(renderer.isAvailable()).thenReturn(true);
        when(renderer.inspect(anyString(), eq(375), eq(834))).thenReturn(problem());
        when(ai.generateHtmlWithImages(anyString(), anyString(), any())).thenReturn(valid());
        assertTrue(service.renderPageHtml(page).contains("/api/assets/artwork/7/character.png"));
    }

    private String withChrome() {
        return valid().replace("<head>", "<head><meta name='wf-renderer' content='ai-static-v1'>")
                .replace("</body>", "<div class='status'><span>08:30</span></div><nav data-wf-navigation='bar'><button data-nav-item='1' data-nav='商城'>商城</button><button data-nav-item='1' data-nav='商品详情'>详情</button></nav></body>");
    }

    @Test void existingGeneratedPagesCanBeAlignedWithoutCallingTheModelOrReplacingTheirContent() {
        String original = withChrome(); page.setHtmlContent(original);
        when(pages.update(isNull(), any())).thenReturn(1);
        assertEquals(1, service.normalizeProjectChrome(7L));
        assertTrue(page.getHtmlContent().contains("wf-project-chrome"));
        assertTrue(page.getHtmlContent().contains("wf-p1-e10") || page.getHtmlContent().contains("data-wf-element-id=\"10\""));
        assertTrue(page.getHtmlContent().contains(">购买</button>"));
        verifyNoInteractions(ai);
        verify(pages, never()).updateById(any());
    }

    @Test void aConcurrentHtmlSavePreventsSharedChromeFromOverwritingThePage() {
        String original = withChrome(); page.setHtmlContent(original);
        when(pages.update(isNull(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.normalizeProjectChrome(7L));
        assertEquals(original, page.getHtmlContent());
        verifyNoInteractions(ai);
    }
}
