package com.example.refactoring.mcp;

import com.example.refactoring.core.PullUpMethodRefactoring;
import com.example.refactoring.core.RefactoringResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * MCP (Model Context Protocol) 服务器实现
 * 提供 pull_up_method 和 restore_snapshot 两个工具功能
 */
public class MCPServer {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPServer.class);
    private static final String VERSION = "1.0.0";
    private static final String SERVER_NAME = "Pull-Up-Method Refactoring MCP Server";
    
    private final ObjectMapper objectMapper;
    private final PullUpMethodRefactoring refactoring;
    private final BufferedReader reader;
    private final PrintWriter writer;
    
    public MCPServer() {
        this.objectMapper = new ObjectMapper();
        this.refactoring = new PullUpMethodRefactoring();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.writer = new PrintWriter(System.out, true);
    }
    
    public static void main(String[] args) {
        MCPServer server = new MCPServer();
        server.run();
    }
    
    public void run() {
        logger.info("Starting MCP Server: {}", SERVER_NAME);
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    JsonNode request = objectMapper.readTree(line);
                    JsonNode response = handleRequest(request);
                    
                    if (response != null) {
                        writer.println(objectMapper.writeValueAsString(response));
                        writer.flush();
                    }
                } catch (Exception e) {
                    logger.error("Error processing request: {}", e.getMessage(), e);
                    JsonNode errorResponse = createErrorResponse(null, -32603, "Internal error", e.getMessage());
                    writer.println(objectMapper.writeValueAsString(errorResponse));
                    writer.flush();
                }
            }
        } catch (IOException e) {
            logger.error("IO error: {}", e.getMessage(), e);
        }
    }
    
    private JsonNode handleRequest(JsonNode request) {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        JsonNode id = request.path("id");
        
        logger.debug("Handling request: method={}, id={}", method, id);
        
        switch (method) {
            case "initialize":
                return handleInitialize(id, params);
            case "tools/list":
                return handleToolsList(id, params);
            case "tools/call":
                return handleToolsCall(id, params);
            default:
                return createErrorResponse(id, -32601, "Method not found", "Unknown method: " + method);
        }
    }
    
    private JsonNode handleInitialize(JsonNode id, JsonNode params) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", createCapabilities());
        result.put("serverInfo", createServerInfo());
        
        response.set("result", result);
        return response;
    }
    
    private JsonNode handleToolsList(JsonNode id, JsonNode params) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = objectMapper.createArrayNode();
        
        // Add pull_up_method tool
        ObjectNode pullUpMethodTool = objectMapper.createObjectNode();
        pullUpMethodTool.put("name", "pull_up_method");
        pullUpMethodTool.put("description", "Execute Pull-Up-Method refactoring operation, move methods from child classes to parent classes");
        
        ObjectNode pullUpMethodInputSchema = objectMapper.createObjectNode();
        pullUpMethodInputSchema.put("type", "object");
        pullUpMethodInputSchema.put("required", objectMapper.createArrayNode()
            .add("projectRoot")
            .add("className")
            .add("methodName"));
        
        ObjectNode pullUpMethodProperties = objectMapper.createObjectNode();
        pullUpMethodProperties.set("projectRoot", createStringProperty("Project root directory path, multiple paths separated by comma", true));
        pullUpMethodProperties.set("className", createStringProperty("Child class name containing the method to be pulled up", true));
        pullUpMethodProperties.set("methodName", createStringProperty("Method name to be pulled up", true));
        pullUpMethodProperties.set("targetAncestorClassName", createStringProperty("Target ancestor class name (optional, defaults to direct parent)", false));
        pullUpMethodProperties.set("outputPath", createStringProperty("Output directory path (optional, defaults to overwrite original files)", false));
        
        pullUpMethodInputSchema.set("properties", pullUpMethodProperties);
        pullUpMethodTool.set("inputSchema", pullUpMethodInputSchema);
        tools.add(pullUpMethodTool);
        
        // Add restore_snapshot tool
        ObjectNode restoreSnapshotTool = objectMapper.createObjectNode();
        restoreSnapshotTool.put("name", "restore_snapshot");
        restoreSnapshotTool.put("description", "Restore files from the last refactoring snapshot");
        
        ObjectNode restoreSnapshotInputSchema = objectMapper.createObjectNode();
        restoreSnapshotInputSchema.put("type", "object");
        restoreSnapshotInputSchema.put("required", objectMapper.createArrayNode().add("projectRoot"));
        
        ObjectNode restoreSnapshotProperties = objectMapper.createObjectNode();
        restoreSnapshotProperties.set("projectRoot", createStringProperty("Project root directory path for locating snapshot files", true));
        
        restoreSnapshotInputSchema.set("properties", restoreSnapshotProperties);
        restoreSnapshotTool.set("inputSchema", restoreSnapshotInputSchema);
        tools.add(restoreSnapshotTool);
        
        result.set("tools", tools);
        response.set("result", result);
        return response;
    }
    
    private JsonNode handleToolsCall(JsonNode id, JsonNode params) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");
        
        logger.info("Calling tool: {}", toolName);
        
        try {
            switch (toolName) {
                case "pull_up_method":
                    return handlePullUpMethod(id, arguments);
                case "restore_snapshot":
                    return handleRestoreSnapshot(id, arguments);
                default:
                    return createErrorResponse(id, -32601, "Tool not found", "Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            logger.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
            return createErrorResponse(id, -32603, "Tool execution error", e.getMessage());
        }
    }
    
    private JsonNode handlePullUpMethod(JsonNode id, JsonNode arguments) {
        String projectRoot = arguments.path("projectRoot").asText();
        String className = arguments.path("className").asText();
        String methodName = arguments.path("methodName").asText();
        String targetAncestorClassName = arguments.path("targetAncestorClassName").asText();
        String outputPath = arguments.path("outputPath").asText();
        
        if (projectRoot.isEmpty() || className.isEmpty() || methodName.isEmpty()) {
            return createErrorResponse(id, -32602, "Invalid parameters", 
                "Missing required parameters: projectRoot, className, methodName");
        }
        
        // Convert empty string to null for outputPath to ensure proper behavior
        if (outputPath != null && outputPath.trim().isEmpty()) {
            outputPath = null;
        }
        
        List<String> sourcePaths = Arrays.asList(projectRoot.split(","));
        sourcePaths = sourcePaths.stream()
            .map(String::trim)
            .filter(path -> !path.isEmpty())
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        logger.info("Executing pull_up_method: projectRoot={}, className={}, methodName={}, targetAncestor={}", 
            sourcePaths, className, methodName, targetAncestorClassName);
        
        RefactoringResult result;
        if (targetAncestorClassName != null && !targetAncestorClassName.isEmpty()) {
            result = refactoring.pullUpMethodToAncestor(sourcePaths, className, methodName, targetAncestorClassName, outputPath);
        } else {
            result = refactoring.pullUpMethod(sourcePaths, className, methodName, outputPath);
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ObjectNode toolResult = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        StringBuilder resultText = new StringBuilder();
        if (result.isSuccess()) {
            resultText.append("✓ Refactoring successful!\n");
            resultText.append("  ").append(result.getMessage()).append("\n");
            
            if (!result.getModifiedFiles().isEmpty()) {
                resultText.append("  Modified files:\n");
                result.getModifiedFiles().forEach(file -> resultText.append("    ").append(file).append("\n"));
            }
            
            if (!result.getWarnings().isEmpty()) {
                resultText.append("  Warnings:\n");
                result.getWarnings().forEach(warning -> resultText.append("    ⚠ ").append(warning).append("\n"));
            }
        } else {
            resultText.append("✗ Refactoring failed!\n");
            resultText.append("  ").append(result.getMessage());
        }
        
        textContent.put("text", resultText.toString());
        content.add(textContent);
        toolResult.set("content", content);
        toolResult.put("isError", !result.isSuccess());
        
        response.set("result", toolResult);
        return response;
    }
    
    private JsonNode handleRestoreSnapshot(JsonNode id, JsonNode arguments) {
        String projectRoot = arguments.path("projectRoot").asText();
        
        if (projectRoot.isEmpty()) {
            return createErrorResponse(id, -32602, "Invalid parameters", "Missing required parameter: projectRoot");
        }
        
        List<String> sourcePaths = Arrays.asList(projectRoot.split(","));
        sourcePaths = sourcePaths.stream()
            .map(String::trim)
            .filter(path -> !path.isEmpty())
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        logger.info("Executing restore_snapshot: projectRoot={}", sourcePaths);
        
        boolean success = refactoring.restoreSnapshot(sourcePaths);
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ObjectNode toolResult = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        String resultText;
        if (success) {
            resultText = "✓ Successfully restored files from snapshot";
        } else {
            resultText = "✗ No snapshot found or restore failed";
        }
        
        textContent.put("text", resultText);
        content.add(textContent);
        toolResult.set("content", content);
        toolResult.put("isError", !success);
        
        response.set("result", toolResult);
        return response;
    }
    
    private ObjectNode createStringProperty(String description, boolean required) {
        ObjectNode property = objectMapper.createObjectNode();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
    
    private ObjectNode createCapabilities() {
        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode tools = objectMapper.createObjectNode();
        tools.put("listChanged", true);
        capabilities.set("tools", tools);
        return capabilities;
    }
    
    private ObjectNode createServerInfo() {
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", VERSION);
        return serverInfo;
    }
    
    private ObjectNode createErrorResponse(JsonNode id, int code, String message, String data) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        
        response.set("error", error);
        return response;
    }
}
