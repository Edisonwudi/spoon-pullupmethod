package com.example.refactoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * POM 依赖管理器：
 * - 扫描修改后的 Java 文件的 import 语句
 * - 基于包前缀→模块映射，检查并为所属模块的 pom.xml 追加缺失的跨模块依赖
 * - 版本使用 ${project.version}
 *
 * 设计为幂等：已存在相同 artifactId 依赖则跳过
 */
public class PomDependencyManager {

    private static final Logger logger = LoggerFactory.getLogger(PomDependencyManager.class);

    /**
     * 包前缀到 artifactId 的映射。此处内置 jhotdraw 常见模块映射，可按需扩展或外部注入。
     */
    private ModuleResolver moduleResolver;

    /**
     * 针对修改的文件，自动修复所属模块 pom.xml 中缺失的跨模块依赖。
     */
    public void fixMissingModuleDependencies(List<String> modifiedFiles, List<String> projectRoots) {
        if (modifiedFiles == null || modifiedFiles.isEmpty()) return;
        try {
            // 构建通用模块解析器
            ModuleResolver resolver = new ModuleResolver(projectRoots);
            this.moduleResolver = resolver;
            
            for (String filePath : modifiedFiles) {
                if (filePath == null) continue;
                File javaFile = new File(filePath);
                if (!javaFile.exists()) continue;

                File moduleRoot = locateModuleRoot(javaFile);
                if (moduleRoot == null) continue;

                Set<String> neededArtifactIds = detectNeededArtifacts(javaFile, resolver);
                if (neededArtifactIds.isEmpty()) continue;

                File pom = new File(moduleRoot, "pom.xml");
                if (!pom.exists()) continue;

                ensureDependenciesInPom(pom, neededArtifactIds);
            }
        } catch (Exception e) {
            logger.warn("自动修复 POM 依赖失败: {}", e.getMessage());
        }
    }

    private File locateModuleRoot(File file) {
        try {
            File dir = file.getParentFile();
            while (dir != null) {
                File pom = new File(dir, "pom.xml");
                if (pom.exists()) return dir;
                dir = dir.getParentFile();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private Set<String> detectNeededArtifacts(File javaFile, ModuleResolver resolver) {
        Set<String> artifacts = new LinkedHashSet<>();
        try {
            List<String> lines = Files.readAllLines(javaFile.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ") || !trimmed.endsWith(";")) continue;
                String qname = trimmed.substring(7, trimmed.length() - 1).trim(); // remove 'import ' and trailing ';'
                // 忽略 static 导入
                if (qname.startsWith("static ")) {
                    qname = qname.substring("static ".length()).trim();
                }
                ModuleResolver.ModuleCoords coords = resolver.resolveByQualifiedName(qname);
                if (coords != null && coords.artifactId != null && !coords.artifactId.isEmpty()) {
                    artifacts.add(coords.artifactId);
                }
            }
        } catch (Exception e) {
            logger.debug("解析文件导入失败: {}", e.getMessage());
        }
        return artifacts;
    }

    private void ensureDependenciesInPom(File pomFile, Set<String> artifactIds) {
        try (FileInputStream fis = new FileInputStream(pomFile)) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(fis);

            Element project = doc.getDocumentElement();
            if (project == null) return;

            Element dependencies = findFirstChild(project, "dependencies");
            if (dependencies == null) {
                dependencies = doc.createElement("dependencies");
                project.appendChild(dependencies);
            }

            Set<String> existing = collectExistingArtifactIds(dependencies);

            boolean changed = false;
            for (String artifactId : artifactIds) {
                if (existing.contains(artifactId)) continue;
                Element dep = doc.createElement("dependency");

                Element group = doc.createElement("groupId");
                group.setTextContent(resolveGroupIdForArtifact(pomFile, artifactId));
                dep.appendChild(group);

                Element art = doc.createElement("artifactId");
                art.setTextContent(artifactId);
                dep.appendChild(art);

                Element ver = doc.createElement("version");
                ver.setTextContent("${project.version}");
                dep.appendChild(ver);

                dependencies.appendChild(dep);
                existing.add(artifactId);
                changed = true;
                logger.info("为 {} 追加依赖: {}:{}:${project.version}", pomFile.getAbsolutePath(), resolveGroupIdForArtifact(pomFile, artifactId), artifactId);
            }

            if (changed) {
                try (FileOutputStream fos = new FileOutputStream(pomFile)) {
                    TransformerFactory tf = TransformerFactory.newInstance();
                    Transformer t = tf.newTransformer();
                    t.setOutputProperty(OutputKeys.INDENT, "yes");
                    t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                    t.transform(new DOMSource(doc), new StreamResult(fos));
                }
            }
        } catch (Exception e) {
            logger.warn("更新 POM 失败: {}", e.getMessage());
        }
    }

    private String resolveGroupIdForArtifact(File currentPom, String artifactId) {
        // 优先尝试使用当前项目根的父 groupId（常见 monorepo 结构共享 groupId）
        try {
            File root = currentPom.getParentFile();
            while (root != null) {
                File pom = new File(root, "pom.xml");
                if (pom.exists()) {
                    try (FileInputStream fis = new FileInputStream(pom)) {
                        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                        dbf.setNamespaceAware(false);
                        DocumentBuilder db = dbf.newDocumentBuilder();
                        Document doc = db.parse(fis);
                        Element project = doc.getDocumentElement();
                        if (project != null) {
                            String groupId = findFirstChildText(project, "groupId");
                            if (groupId == null || groupId.isEmpty()) {
                                Element parent = findFirstChild(project, "parent");
                                if (parent != null) {
                                    groupId = findFirstChildText(parent, "groupId");
                                }
                            }
                            if (groupId != null && !groupId.isEmpty()) {
                                return groupId;
                            }
                        }
                    }
                }
                root = root.getParentFile();
            }
        } catch (Exception ignore) {}
        // 兜底：常见默认 groupId
        return "org.jhotdraw";
    }

    private Element findFirstChild(Element parent, String name) {
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n instanceof Element && name.equals(n.getNodeName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private Set<String> collectExistingArtifactIds(Element dependencies) {
        Set<String> ids = new HashSet<>();
        NodeList deps = dependencies.getChildNodes();
        for (int i = 0; i < deps.getLength(); i++) {
            Node n = deps.item(i);
            if (!(n instanceof Element)) continue;
            Element dep = (Element) n;
            if (!"dependency".equals(dep.getNodeName())) continue;
            String artifactId = null;
            NodeList children = dep.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node c = children.item(j);
                if (c instanceof Element) {
                    Element e = (Element) c;
                    if ("artifactId".equals(e.getNodeName())) {
                        artifactId = e.getTextContent();
                        break;
                    }
                }
            }
            if (artifactId != null && !artifactId.isEmpty()) {
                ids.add(artifactId.trim());
            }
        }
        return ids;
    }

    private String findFirstChildText(Element parent, String name) {
        Element e = findFirstChild(parent, name);
        return e != null ? e.getTextContent() : null;
    }
}


