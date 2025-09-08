package com.example.refactoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 通用模块解析器：从项目根自动构建 包前缀 -> (groupId, artifactId, moduleRoot) 索引。
 * 通过扫描各模块的 pom.xml 与 src/main/java 下的包声明来建立映射。
 */
public class ModuleResolver {

    private static final Logger logger = LoggerFactory.getLogger(ModuleResolver.class);

    public static class ModuleCoords {
        public final String groupId;
        public final String artifactId;
        public final File moduleRoot;
        public ModuleCoords(String groupId, String artifactId, File moduleRoot) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.moduleRoot = moduleRoot;
        }
    }

    private final Map<String, ModuleCoords> packagePrefixToModule = new HashMap<>();

    public ModuleResolver(List<String> projectRoots) {
        try {
            Set<File> moduleRoots = discoverModuleRoots(projectRoots);
            for (File moduleRoot : moduleRoots) {
                ModuleCoords coords = readModuleCoords(moduleRoot);
                if (coords == null) continue;
                for (String pkg : discoverPackagesUnderModule(moduleRoot)) {
                    // 记录最长优先匹配的包前缀
                    packagePrefixToModule.put(pkg + ".", coords);
                }
            }
            logger.info("模块解析完成，索引包前缀数量: {}", packagePrefixToModule.size());
        } catch (Exception e) {
            logger.warn("构建模块索引失败: {}", e.getMessage());
        }
    }

    /**
     * 根据全限定名解析所在模块坐标（尽量使用最长匹配的包前缀）。
     */
    public ModuleCoords resolveByQualifiedName(String qualifiedName) {
        if (qualifiedName == null) return null;
        String qn = qualifiedName;
        // 去掉 static 前缀
        if (qn.startsWith("static ")) qn = qn.substring(7);
        // 逐步缩短以进行最长前缀匹配
        String[] parts = qn.split("\\.");
        for (int end = parts.length - 1; end >= 1; end--) {
            String prefix = String.join(".", Arrays.copyOf(parts, end));
            ModuleCoords coords = packagePrefixToModule.get(prefix + ".");
            if (coords != null) return coords;
        }
        return null;
    }

    private Set<File> discoverModuleRoots(List<String> projectRoots) {
        Set<File> roots = new LinkedHashSet<>();
        for (String root : projectRoots) {
            File f = new File(root);
            if (!f.exists()) continue;
            File top = f.isDirectory() ? f : f.getParentFile();
            collectModulesRecursively(top, roots, new HashSet<>());
        }
        return roots;
    }

    private void collectModulesRecursively(File dir, Set<File> out, Set<String> visited) {
        if (dir == null || !dir.isDirectory()) return;
        String key = dir.getAbsolutePath();
        if (!visited.add(key)) return;
        File pom = new File(dir, "pom.xml");
        if (pom.exists()) {
            out.add(dir);
        }
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (!child.isDirectory()) continue;
            if ("target".equals(name) || ".git".equals(name) || ".idea".equals(name) ||
                "node_modules".equals(name) || ".gradle".equals(name)) {
                continue;
            }
            collectModulesRecursively(child, out, visited);
        }
    }

    private ModuleCoords readModuleCoords(File moduleRoot) {
        try (FileInputStream fis = new FileInputStream(new File(moduleRoot, "pom.xml"))) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(fis);
            Element project = doc.getDocumentElement();
            if (project == null) return null;

            String artifactId = getFirstChildText(project, "artifactId");
            String groupId = getFirstChildText(project, "groupId");
            if (groupId == null || groupId.isEmpty()) {
                // 继承自 parent
                Element parent = findFirstChild(project, "parent");
                if (parent != null) {
                    groupId = getFirstChildText(parent, "groupId");
                }
            }
            if (artifactId == null || artifactId.isEmpty()) return null;
            if (groupId == null || groupId.isEmpty()) groupId = "";
            return new ModuleCoords(groupId, artifactId, moduleRoot);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> discoverPackagesUnderModule(File moduleRoot) {
        List<String> pkgs = new ArrayList<>();
        try {
            File src = new File(moduleRoot, "src/main/java");
            if (!src.exists()) return pkgs;
            Files.walk(src.toPath())
                .filter(p -> p.toString().endsWith(".java"))
                .limit(5000) // 防止极端大仓库过慢
                .forEach(p -> {
                    try {
                        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                        for (String line : lines) {
                            String t = line.trim();
                            if (t.startsWith("package ") && t.endsWith(";")) {
                                String pkg = t.substring(8, t.length() - 1).trim();
                                if (!pkg.isEmpty()) pkgs.add(pkg);
                                break;
                            }
                        }
                    } catch (Exception ignore) {}
                });
        } catch (Exception ignore) {}
        // 去重并按长度降序，方便最长前缀匹配
        Set<String> uniq = new LinkedHashSet<>(pkgs);
        List<String> sorted = new ArrayList<>(uniq);
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return sorted;
    }

    private Element findFirstChild(Element parent, String name) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element) {
                Element e = (Element) parent.getChildNodes().item(i);
                if (name.equals(e.getNodeName())) return e;
            }
        }
        return null;
    }

    private String getFirstChildText(Element parent, String name) {
        Element e = findFirstChild(parent, name);
        return e != null ? e.getTextContent() : null;
    }
}


