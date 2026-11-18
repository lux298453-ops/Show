package com.wireforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.mapper.PageMapper;
import com.wireforge.mapper.ProjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PageSaveIsolationTest {
    private final PageMapper pages = mock(PageMapper.class);
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final ProjectService service = new ProjectService(projects, pages, null, null, null, null,
            null, null, null, null, null, null, null);
    private final Page page = new Page();

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "page-save-isolation"), Page.class);
        page.setId(91001L); page.setProjectId(91L); page.setHtmlContent("original"); page.setIsDeleted(0);
        page.setBackgroundImage("/files/original.png"); page.setCanvasX(10d); page.setCanvasY(20d);
        page.setCanvasWidth(375); page.setCanvasHeight(812);
        Project project = new Project(); project.setId(91L);
        when(projects.selectById(91L)).thenReturn(project);
        when(pages.selectById(91001L)).thenReturn(page);
        when(pages.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @Test void positionAndSizeWritesCannotCarryHtmlOrOtherPageFields() {
        var position = service.updatePagePosition(91L, 91001L, Map.of("canvasX", 100d, "canvasY", 200d));
        assertEquals(100d, position.get("canvasX")); assertEquals(200d, position.get("canvasY"));
        var size = service.updatePageSize(91L, 91001L, Map.of("width", 420, "height", 900));
        assertEquals(420, size.get("canvasWidth")); assertEquals(900, size.get("canvasHeight"));
        var captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pages, times(2)).update(isNull(), captor.capture());
        assertEquals(Map.of("canvas_x", 100d, "canvas_y", 200d), assignments(captor.getAllValues().get(0)));
        assertEquals(Map.of("canvas_width", 420, "canvas_height", 900), assignments(captor.getAllValues().get(1)));
        for (var update : captor.getAllValues()) {
            assertTrue(update.getSqlSegment().contains("project_id"));
            assertTrue(update.getParamNameValuePairs().containsValue(91L));
        }
        verify(pages, never()).updateById(any());
    }

    @Test void omittedPositionCoordinatesStayUnchangedAndExplicitNullStillClearsPosition() {
        service.updatePagePosition(91L, 91001L, Map.of());
        verify(pages, never()).update(isNull(), any(Wrapper.class));
        var body = new HashMap<String, Object>(); body.put("canvasX", null);
        var result = service.updatePagePosition(91L, 91001L, body);
        var captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pages).update(isNull(), captor.capture());
        assertEquals(1, assignments(captor.getValue()).size());
        assertTrue(assignments(captor.getValue()).containsKey("canvas_x"));
        assertNull(result.get("canvasX")); assertEquals(20d, result.get("canvasY"));
    }

    @Test void manualHtmlSaveUsesOnlyHtmlAndGuardsTheLoadedBaselineIncludingNull() {
        service.updatePageHtml(91L, 91001L, "new html", "editor", null, null);
        page.setHtmlContent(null);
        service.updatePageHtml(91L, 91001L, "first html", "editor", null, null);
        var captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pages, times(2)).update(isNull(), captor.capture());
        assertEquals(Map.of("html_content", "new html"), assignments(captor.getAllValues().get(0)));
        assertTrue(captor.getAllValues().get(0).getSqlSegment().contains("BINARY html_content"));
        assertTrue(captor.getAllValues().get(0).getParamNameValuePairs().containsValue("original"));
        assertEquals(Map.of("html_content", "first html"), assignments(captor.getAllValues().get(1)));
        assertTrue(captor.getAllValues().get(1).getSqlSegment().contains("html_content IS NULL"));
        verify(pages, never()).updateById(any());
    }

    @Test void concurrentPositionAndHtmlSavesPreserveBothChangesInEitherOrder() throws Exception {
        interleavedSave(false);
        interleavedSave(true);
    }

    private void interleavedSave(boolean htmlReadsFirst) throws Exception {
        var stored = new AtomicReference<>(copy(page));
        var firstRead = new CountDownLatch(1);
        var continueFirst = new CountDownLatch(1);
        when(pages.selectById(91001L)).thenAnswer(inv -> {
            Page loaded = copy(stored.get());
            if (Thread.currentThread().getName().equals("first-save")) {
                firstRead.countDown(); assertTrue(continueFirst.await(5, TimeUnit.SECONDS));
            }
            return loaded;
        });
        // Apply only columns present in the real generated SET clause; a whole-row write loses the race.
        doAnswer(inv -> {
            var fields = assignments(inv.getArgument(1));
            stored.updateAndGet(current -> {
                Page next = copy(current);
                if (fields.containsKey("html_content")) next.setHtmlContent((String) fields.get("html_content"));
                if (fields.containsKey("canvas_x")) next.setCanvasX((Double) fields.get("canvas_x"));
                if (fields.containsKey("canvas_y")) next.setCanvasY((Double) fields.get("canvas_y"));
                return next;
            });
            return 1;
        }).when(pages).update(isNull(), any(Wrapper.class));
        doAnswer(inv -> { stored.set(copy(inv.getArgument(0))); return 1; }).when(pages).updateById(any(Page.class));
        var worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "first-save"));
        Runnable saveHtml = () -> service.updatePageHtml(91L, 91001L, "latest html", "editor", null, null);
        Runnable savePosition = () -> service.updatePagePosition(91L, 91001L, Map.of("canvasX", 100d, "canvasY", 200d));
        try {
            var first = worker.submit(htmlReadsFirst ? saveHtml : savePosition);
            assertTrue(firstRead.await(5, TimeUnit.SECONDS));
            (htmlReadsFirst ? savePosition : saveHtml).run();
            continueFirst.countDown(); first.get(5, TimeUnit.SECONDS);
            assertEquals("latest html", stored.get().getHtmlContent());
            assertEquals(100d, stored.get().getCanvasX()); assertEquals(200d, stored.get().getCanvasY());
            assertEquals("/files/original.png", stored.get().getBackgroundImage());
        } finally { continueFirst.countDown(); worker.shutdownNow(); }
    }

    @Test void concurrentHtmlChangeOrDeletionCannotBeReportedAsSaved() {
        when(pages.update(isNull(), any(Wrapper.class))).thenReturn(0);
        Page latest = copy(page); latest.setHtmlContent("changed elsewhere");
        when(pages.selectById(91001L)).thenReturn(page, latest);
        assertThrows(IllegalStateException.class, () -> service.updatePageHtml(91L, 91001L, "my edit", "editor", null, null));
        latest.setHtmlContent("my edit"); latest.setIsDeleted(1);
        when(pages.selectById(91001L)).thenReturn(page, latest);
        assertThrows(IllegalStateException.class, () -> service.updatePageHtml(91L, 91001L, "my edit", "editor", null, null));
    }

    @Test void deletedPagesAndInvalidSizesDoNotReceiveUpdates() {
        assertThrows(IllegalArgumentException.class, () -> service.updatePageSize(91L, 91001L, Map.of("width", 1, "height", 812)));
        page.setIsDeleted(1);
        assertThrows(IllegalStateException.class, () -> service.updatePagePosition(91L, 91001L, Map.of("canvasX", 1d)));
        assertThrows(IllegalStateException.class, () -> service.updatePageSize(91L, 91001L, Map.of("width", 375, "height", 812)));
        verify(pages, never()).update(isNull(), any(Wrapper.class));
    }

    private static Page copy(Page page) { Page result = new Page(); BeanUtils.copyProperties(page, result); return result; }

    private static Map<String, Object> assignments(LambdaUpdateWrapper<?> update) {
        Map<String, Object> result = new HashMap<>();
        var matcher = Pattern.compile("(\\w+)=#\\{ew\\.paramNameValuePairs\\.(\\w+)}").matcher(update.getSqlSet());
        while (matcher.find()) result.put(matcher.group(1), update.getParamNameValuePairs().get(matcher.group(2)));
        return result;
    }
}
