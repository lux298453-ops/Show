package com.wireforge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DesignDirectoryTest {
    @TempDir Path root;

    @Test void listsRelativeFoldersAndSkipsAssetsAndInternalCaches() throws Exception {
        Files.createDirectories(root.resolve("OPPO/家园"));
        Files.createDirectories(root.resolve("荣耀"));
        Files.createDirectories(root.resolve("assets/原图"));
        Files.createDirectories(root.resolve("OPPO/.wireforge-artwork/180"));
        Files.createDirectories(root.resolve(".cache"));
        assertEquals(List.of(".", "OPPO", "OPPO/家园", "荣耀"), DesignDirectory.list(root.toString()));
    }

    @Test void rootRequiresExplicitSelectionAndEmptySelectionStaysUnbound() {
        assertNull(DesignDirectory.normalize(root.toString(), ""));
        assertNull(DesignDirectory.normalize(root.toString(), null));
        assertThrows(IllegalStateException.class, () -> DesignDirectory.resolve(root.toString(), ""));
        assertEquals(root.toAbsolutePath(), DesignDirectory.resolve(root.toString(), "."));
    }

    @Test void rejectsTraversalAbsolutePathsAndExcludedFolders() throws Exception {
        Files.createDirectories(root.resolve("OPPO"));
        Files.createDirectories(root.resolve("assets"));
        Files.createDirectories(root.resolve(".cache"));
        for (String folder : List.of("../", "OPPO/../", "OPPO/../../", "/tmp", "C:\\Windows", "\\\\server\\share", "assets", ".cache", "OPPO//")) {
            assertThrows(IllegalArgumentException.class, () -> DesignDirectory.resolve(root.toString(), folder), folder);
        }
    }

    @Test void missingFolderAndFileCannotBecomeScanRoots() throws Exception {
        Files.writeString(root.resolve("file.png"), "image");
        assertThrows(IllegalStateException.class, () -> DesignDirectory.resolve(root.toString(), "missing"));
        assertThrows(IllegalArgumentException.class, () -> DesignDirectory.resolve(root.toString(), "file.png"));
        assertThrows(IllegalStateException.class, () -> DesignDirectory.list(root.resolve("missing").toString()));
    }
}
