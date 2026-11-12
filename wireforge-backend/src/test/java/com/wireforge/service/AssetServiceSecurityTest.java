package com.wireforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.StoredAsset;
import com.wireforge.mapper.StoredAssetMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssetServiceSecurityTest {
    @TempDir Path designs;
    private StoredAssetMapper mapper;
    private AssetService service;
    private static final byte[] SAFE = "<svg xmlns='http://www.w3.org/2000/svg'><rect width='10' height='10' fill='#f80'/></svg>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ACTIVE = "<svg xmlns='http://www.w3.org/2000/svg' onload='window.__svgProbe=1'/>".getBytes(StandardCharsets.UTF_8);

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "asset-security-test"), StoredAsset.class);
    }

    @BeforeEach void setup() {
        mapper = mock(StoredAssetMapper.class);
        service = new AssetService(new DefaultResourceLoader(), new ObjectMapper(), mapper, "classpath:assets/wireframe", designs.toString());
    }

    @Test void unsafeSvgIsRejectedBeforeDatabaseOrDiskWrites() {
        assertThrows(IllegalArgumentException.class, () -> service.saveUploadedAsset("uploads", "attack.SVG", ACTIVE));
        verifyNoInteractions(mapper);
        assertFalse(Files.exists(designs.resolve("assets")));
    }

    @Test void safeSvgRetainsItsBytesAndCanBeReadFromDatabaseOrDisk() throws Exception {
        var metadata = service.saveUploadedAsset("icons", "icon.svg", SAFE, "user", "用户");
        var captured = ArgumentCaptor.forClass(StoredAsset.class);
        verify(mapper).insert(captured.capture());
        StoredAsset saved = captured.getValue();
        assertArrayEquals(SAFE, saved.getData());
        assertEquals("image/svg+xml", saved.getContentType());
        assertArrayEquals(SAFE, Files.readAllBytes(designs.resolve("assets/icons/icon.svg")));
        when(mapper.selectOne(any())).thenReturn(saved);
        assertArrayEquals(SAFE, service.getAssetResource(metadata.get("assetId").toString()).getContentAsByteArray());
        when(mapper.selectOne(any())).thenReturn(null);
        assertArrayEquals(SAFE, service.getAssetResource("u-icons-icon").getContentAsByteArray());
    }

    @Test void unsafeLegacyDatabaseAssetsAreBlockedWithoutFallingBackToDisk() throws Exception {
        Files.createDirectories(designs.resolve("assets"));
        Files.write(designs.resolve("assets/legacy.svg"), SAFE);
        for (String[] kind : new String[][]{{"legacy.SVG", "image/svg+xml"}, {"legacy", "image/svg+xml;charset=UTF-8"}}) {
            when(mapper.selectOne(any())).thenReturn(StoredAsset.builder().fileName(kind[0]).contentType(kind[1]).data(ACTIVE).build());
            assertThrows(IllegalArgumentException.class, () -> service.getAssetResource("u-legacy"));
        }
        verify(mapper, never()).updateById(any());
        assertArrayEquals(SAFE, Files.readAllBytes(designs.resolve("assets/legacy.svg")));
    }

    @Test void unsafeLegacyDiskCopiesAreBlockedEvenWhenTheDatabaseIsUnavailable() throws Exception {
        Files.createDirectories(designs.resolve("assets"));
        Files.write(designs.resolve("assets/legacy.svg"), ACTIVE);
        when(mapper.selectOne(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalArgumentException.class, () -> service.getAssetResource("u-legacy"));
    }

    @Test void ordinaryPngUploadsAndReadsRemainUnchanged() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5QAAAABJRU5ErkJggg==");
        service.saveUploadedAsset("avatars", "pet.png", png);
        var captured = ArgumentCaptor.forClass(StoredAsset.class);
        verify(mapper).insert(captured.capture());
        when(mapper.selectOne(any())).thenReturn(captured.getValue());
        assertArrayEquals(png, service.getAssetResource(captured.getValue().getAssetId()).getContentAsByteArray());
        assertEquals("image/png", captured.getValue().getContentType());
    }
}
