package com.example.refactoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 快照管理器：保存/恢复上一次重构涉及的文件。
 * 约定：只保存一份最新快照；新重构会覆盖快照。
 * 目录：项目根目录下 .refactor-snapshot/
 */
public class SnapshotManager {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotManager.class);

    private static final String SNAPSHOT_DIR_NAME = ".refactor-snapshot";
    private static final String META_FILE_NAME = "meta.txt"; // 记录时间和文件列表

    /**
     * 根据任意一个源码路径推断项目根目录（取其最近的父目录作为根）。
     * 若传入多个，则取它们的最近公共父目录；若失败，回退到第一个路径的父目录。
     */
    public File determineProjectRoot(List<String> sourcePaths) {
        try {
            if (sourcePaths == null || sourcePaths.isEmpty()) {
                return new File("").getAbsoluteFile();
            }
            Path common = new File(sourcePaths.get(0)).getAbsoluteFile().toPath();
            for (int i = 1; i < sourcePaths.size(); i++) {
                Path p = new File(sourcePaths.get(i)).getAbsoluteFile().toPath();
                common = commonAncestor(common, p);
                if (common == null) {
                    // 没有公共父目录，回退到第一个路径的父目录
                    return new File(sourcePaths.get(0)).getAbsoluteFile().getParentFile();
                }
            }
            File commonFile = common.toFile();
            if (commonFile.isFile()) {
                return commonFile.getParentFile();
            }
            return commonFile;
        } catch (Exception e) {
            logger.warn("推断项目根目录失败，使用当前工作目录", e);
            return new File("").getAbsoluteFile();
        }
    }

    private Path commonAncestor(Path a, Path b) {
        a = a.toAbsolutePath().normalize();
        b = b.toAbsolutePath().normalize();
        int aCount = a.getNameCount();
        int bCount = b.getNameCount();
        int min = Math.min(aCount, bCount);
        int i = 0;
        for (; i < min; i++) {
            if (!a.getName(i).equals(b.getName(i))) {
                break;
            }
        }
        if (i == 0) {
            return a.getRoot();
        }
        return a.getRoot() == null ? a.subpath(0, i) : a.getRoot().resolve(a.subpath(0, i));
    }

    /**
     * 保存快照：将即将被修改的文件复制到 .refactor-snapshot 下，覆盖旧快照。
     */
    public void saveSnapshot(List<String> filesAboutToChange, List<String> sourcePaths) {
        if (filesAboutToChange == null || filesAboutToChange.isEmpty()) {
            return;
        }
        File projectRoot = determineProjectRoot(sourcePaths);
        File snapshotDir = new File(projectRoot, SNAPSHOT_DIR_NAME);

        // 清空旧快照
        deleteDirectory(snapshotDir);
        snapshotDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        File meta = new File(snapshotDir, META_FILE_NAME);

        try {
            StringBuilder metaContent = new StringBuilder();
            metaContent.append("timestamp=").append(timestamp).append('\n');
            metaContent.append("count=").append(filesAboutToChange.size()).append('\n');

            Set<String> written = new HashSet<>();
            for (String filePath : filesAboutToChange) {
                if (filePath == null) continue;
                File src = new File(filePath);
                if (!src.exists() || !src.isFile()) continue;

                String rel = toRelativePathWithinProject(src, projectRoot);
                File dst = new File(snapshotDir, rel);
                if (!dst.getParentFile().exists()) dst.getParentFile().mkdirs();
                copyFile(src, dst);
                metaContent.append(rel).append('\n');
                written.add(rel);
            }

            Files.write(meta.toPath(), metaContent.toString().getBytes(StandardCharsets.UTF_8));
            logger.info("已保存重构快照到: {} ({} 个文件)", snapshotDir.getAbsolutePath(), written.size());
        } catch (Exception e) {
            logger.warn("保存快照失败: {}", e.getMessage());
        }
    }

    /**
     * 恢复快照：将 .refactor-snapshot 中的文件复制回原路径，存在则覆盖。
     */
    public boolean restoreSnapshot(List<String> sourcePaths) {
        File projectRoot = determineProjectRoot(sourcePaths);
        File snapshotDir = new File(projectRoot, SNAPSHOT_DIR_NAME);
        if (!snapshotDir.exists() || !snapshotDir.isDirectory()) {
            logger.warn("未找到快照目录: {}", snapshotDir.getAbsolutePath());
            return false;
        }

        File meta = new File(snapshotDir, META_FILE_NAME);
        if (!meta.exists()) {
            logger.warn("快照元数据缺失: {}", meta.getAbsolutePath());
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(meta.toPath(), StandardCharsets.UTF_8);
            int startIdx = 0;
            for (; startIdx < lines.size(); startIdx++) {
                String line = lines.get(startIdx);
                if (!line.contains("=")) break; // 跳过头两行
            }

            int restored = 0;
            for (int i = startIdx; i < lines.size(); i++) {
                String rel = lines.get(i).trim();
                if (rel.isEmpty()) continue;
                File src = new File(snapshotDir, rel);
                File dst = new File(projectRoot, rel);
                if (!dst.getParentFile().exists()) dst.getParentFile().mkdirs();
                if (src.exists()) {
                    copyFile(src, dst);
                    restored++;
                }
            }
            logger.info("已从快照恢复 {} 个文件", restored);
            return restored > 0;
        } catch (Exception e) {
            logger.error("恢复快照失败", e);
            return false;
        }
    }

    private String toRelativePathWithinProject(File file, File projectRoot) {
        String filePath = file.getAbsolutePath();
        String rootPath = projectRoot.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            String rel = filePath.substring(rootPath.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel;
        }
        return file.getName();
    }

    private void copyFile(File source, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectory(child);
                } else {
                    try { Files.deleteIfExists(child.toPath()); } catch (IOException ignored) {}
                }
            }
        }
        try { Files.deleteIfExists(dir.toPath()); } catch (IOException ignored) {}
    }
}


