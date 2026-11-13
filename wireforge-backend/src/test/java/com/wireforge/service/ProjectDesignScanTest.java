package com.wireforge.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.mapper.PageMapper;
import com.wireforge.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectDesignScanTest {
    @TempDir Path temp;
    final ProjectMapper projects = mock(ProjectMapper.class);
    final PageMapper pages = mock(PageMapper.class);
    final AnalyzeService analyzer = mock(AnalyzeService.class);
    ProjectService service;
    Project project;

    @BeforeAll static void metadata() {
        var builder = new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "design-scan-test");
        TableInfoHelper.initTableInfo(builder, Page.class);
        TableInfoHelper.initTableInfo(builder, Project.class);
    }

    @BeforeEach void setup() {
        service = new ProjectService(projects, pages, null, null, null, null, null, analyzer, null, null, null, null, null);
        project = new Project(); project.setId(7L); project.setDesignFolder(".");
        when(projects.selectById(7L)).thenReturn(project);
        when(pages.selectList(any())).thenReturn(List.of());
        when(projects.selectList(any())).thenReturn(List.of(project));
    }

    private Path image(Path root, String relative) throws Exception {
        Path path = root.resolve(relative); Files.createDirectories(path.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 3, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", path.toFile());
        return path;
    }

    @Test void manualScanExcludesArtworkAssetsAndHiddenGroupsButKeepsRealGroupedDesigns() throws Exception {
        Path root = temp.resolve("designs");
        Path home = image(root, "home.png"), grouped = image(root, "宠物/首页.PNG");
        Path cached = image(root, ".wireforge-artwork/180/character.png");
        image(root, "assets/original.png"); image(root, "宠物/assets/original.png");
        image(root, "宠物/.wireforge-artwork/character.png"); image(root, ".cache/preview.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        List<Page> result = service.scanDesigns(7L);
        assertEquals(List.of(home.toString(), grouped.toString()), result.stream().map(Page::getBackgroundImage).toList());
        assertEquals(List.of("home", "宠物/首页"), result.stream().map(Page::getName).toList());
        verify(pages, times(2)).insert(any(Page.class));
        verifyNoInteractions(analyzer);
        assertTrue(Files.exists(cached), "扫描不能删除角色缓存素材");
    }

    @Test void automaticScanIgnoresArtworkOnlyDirectoriesWithoutCreatingOrAnalyzingPages() throws Exception {
        Path root = temp.resolve("designs"); image(root, ".wireforge-artwork/180/character.png");
        image(root, "assets/original.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        assertNull(service.autoGenerate());
        verify(pages, never()).insert(any(Page.class));
        verify(projects, never()).insert(any(Project.class));
        verifyNoInteractions(analyzer);
    }

    @Test void filteringIsRelativeToTheConfiguredRootAndRetainsDepthAndStableOrdering() throws Exception {
        // An ancestor named assets (or the configured root's own dot-name) is not an internal subdirectory.
        Path root = temp.resolve("assets/.designs");
        Path a = image(root, "a.png"), b = image(root, "分组/子组/b.webp");
        image(root, "分组/子组/过深/c.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        assertEquals(List.of(a.toString(), b.toString()), service.scanDesigns(7L).stream().map(Page::getBackgroundImage).toList());
    }

    @Test void unboundProjectRequiresSelectionWithoutScanningTheRoot() throws Exception {
        Path root = temp.resolve("designs"); image(root, "荣耀/首页.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        project.setDesignFolder(null);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.scanDesigns(7L)).getMessage().contains("选择"));
        verifyNoInteractions(pages, analyzer);
    }

    @Test void boundFolderIncludesItsOwnGroupsAndExcludesOtherProjects() throws Exception {
        Path root = temp.resolve("designs");
        Path home = image(root, "OPPO/首页.png"), shop = image(root, "OPPO/商城/商品.png");
        image(root, "荣耀/首页.png"); image(root, "OPPO/assets/角色.png");
        image(root, "OPPO/.wireforge-artwork/角色.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        project.setDesignFolder("OPPO");
        List<Page> found = service.scanDesigns(7L);
        assertEquals(List.of(shop.toString(), home.toString()), found.stream().map(Page::getBackgroundImage).toList());
        assertEquals(List.of("商城/商品", "首页"), found.stream().map(Page::getName).toList());
        assertTrue(found.stream().allMatch(p -> p.getProjectId().equals(7L)));
        verifyNoInteractions(analyzer);
    }

    @Test void secondScanDoesNotDuplicateExistingPagesOrRegenerateTheirHtml() throws Exception {
        Path root = temp.resolve("designs"); Path original = image(root, "OPPO/首页.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString()); project.setDesignFolder("OPPO");
        Page existing = new Page(); existing.setId(80L); existing.setBackgroundImage(original.toString());
        existing.setHtmlContent("<main>已调整的原型</main>"); existing.setAnalyzed(1);
        when(pages.selectList(any())).thenReturn(List.of(existing));
        assertTrue(service.scanDesigns(7L).isEmpty());
        assertEquals("<main>已调整的原型</main>", existing.getHtmlContent());
        verify(pages, never()).insert(any(Page.class));
        verify(pages, never()).updateById(any(Page.class));
        verifyNoInteractions(analyzer);
    }

    @Test void missingBoundFolderDoesNotFallBackToOtherDesigns() throws Exception {
        Path root = temp.resolve("designs"); image(root, "荣耀/首页.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString()); project.setDesignFolder("OPPO");
        assertThrows(IllegalStateException.class, () -> service.scanDesigns(7L));
        verifyNoInteractions(pages, analyzer);
    }

    @Test void automaticScanSkipsUnboundProjectsInsteadOfImportingIntoTheNewestOne() throws Exception {
        Path root = temp.resolve("designs"); image(root, "OPPO/首页.png"); image(root, "荣耀/首页.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString()); project.setDesignFolder(null);
        assertNull(service.autoGenerate());
        verify(projects, never()).insert(any(Project.class));
        verifyNoInteractions(pages, analyzer);
    }

    @Test void creatingProjectStoresTheChosenFolderWithoutImportingOrGenerating() throws Exception {
        Path root = temp.resolve("designs"); image(root, "OPPO/家园/首页.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        Project created = service.createProject("OPPO测试", "", "OPPO\\家园", null, null);
        assertEquals("OPPO/家园", created.getDesignFolder());
        verify(projects).insert(created);
        verifyNoInteractions(pages, analyzer);
    }

    @Test void changingFolderUpdatesOnlyTheBindingAndKeepsExistingPages() throws Exception {
        Path root = temp.resolve("designs"); image(root, "OPPO/首页.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        Project saved = new Project(); saved.setId(7L); saved.setDesignFolder("OPPO");
        when(projects.selectById(7L)).thenReturn(project, saved);
        assertSame(saved, service.setDesignFolder(7L, "OPPO", null, null));
        var capture = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(projects).update(isNull(), capture.capture());
        assertTrue(((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) capture.getValue()).getSqlSet().startsWith("design_folder="));
        verifyNoInteractions(pages, analyzer);
    }
}
