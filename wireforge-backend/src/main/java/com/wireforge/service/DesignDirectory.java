package com.wireforge.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** 项目仅保存相对目录；目录选择与扫描共用相同边界。 */
final class DesignDirectory {
    private DesignDirectory() {}

    static Path root(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("尚未配置设计稿根目录");
        }
        try {
            Path root = Path.of(configuredRoot).toRealPath();
            if (!Files.isDirectory(root)) throw new IllegalStateException("设计稿根目录不存在");
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("设计稿根目录不存在，请检查配置", e);
        }
    }

    static boolean excluded(Path folder) {
        String name = folder.getFileName().toString();
        return name.startsWith(".") || "assets".equalsIgnoreCase(name);
    }

    static Path resolve(String configuredRoot, String selected) {
        if (selected == null || selected.isBlank()) {
            throw new IllegalStateException("请先为项目选择设计稿文件夹");
        }
        Path root = root(configuredRoot);
        String relative = selected.trim().replace('\\', '/');
        if (relative.length() > 500 || relative.startsWith("/") || relative.contains(":")) {
            throw new IllegalArgumentException("请选择设计稿根目录内的文件夹");
        }
        if (".".equals(relative)) return root;
        Path child = root;
        for (String segment : relative.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || excluded(Path.of(segment))) {
                throw new IllegalArgumentException("请选择有效的设计稿文件夹，素材和缓存目录不参与扫描");
            }
            child = child.resolve(segment);
            try {
                if (Files.isSymbolicLink(child) || !child.toRealPath().startsWith(root)) {
                    throw new IllegalArgumentException("请选择设计稿根目录内的普通文件夹");
                }
            } catch (IOException e) {
                throw new IllegalStateException("所选设计稿文件夹不存在，请重新选择", e);
            }
        }
        if (!Files.isDirectory(child)) throw new IllegalArgumentException("所选路径不是文件夹");
        return child.normalize();
    }

    static String normalize(String configuredRoot, String selected) {
        if (selected == null || selected.isBlank()) return null;
        Path root = root(configuredRoot);
        Path chosen = resolve(configuredRoot, selected);
        return root.equals(chosen) ? "." : root.relativize(chosen).toString().replace('\\', '/');
    }

    static List<String> list(String configuredRoot) {
        Path root = root(configuredRoot);
        List<String> result = new ArrayList<>();
        result.add(".");
        try {
            Files.walkFileTree(root, EnumSet.noneOf(java.nio.file.FileVisitOption.class), 32,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attrs) throws IOException {
                            if (folder.equals(root)) return FileVisitResult.CONTINUE;
                            if (excluded(folder) || !folder.toRealPath().startsWith(root)) return FileVisitResult.SKIP_SUBTREE;
                            result.add(root.relativize(folder).toString().replace('\\', '/'));
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("读取设计稿文件夹失败，请重试", e);
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }
}
