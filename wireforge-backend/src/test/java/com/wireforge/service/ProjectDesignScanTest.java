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

    @BeforeAll static void metadata() {
        var builder = new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "design-scan-test");
        TableInfoHelper.initTableInfo(builder, Page.class);
        TableInfoHelper.initTableInfo(builder, Project.class);
    }

    @BeforeEach void setup() {
        service = new ProjectService(projects, pages, null, null, null, null, null, analyzer, null, null, null, null, null);
        Project project = new Project(); project.setId(7L);
        when(projects.selectById(7L)).thenReturn(project);
        when(pages.selectList(any())).thenReturn(List.of());
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
        verifyNoInteractions(projects, analyzer);
    }

    @Test void filteringIsRelativeToTheConfiguredRootAndRetainsDepthAndStableOrdering() throws Exception {
        // An ancestor named assets (or the configured root's own dot-name) is not an internal subdirectory.
        Path root = temp.resolve("assets/.designs");
        Path a = image(root, "a.png"), b = image(root, "分组/子组/b.webp");
        image(root, "分组/子组/过深/c.png");
        ReflectionTestUtils.setField(service, "designsDir", root.toString());
        assertEquals(List.of(a.toString(), b.toString()), service.scanDesigns(7L).stream().map(Page::getBackgroundImage).toList());
    }
}
