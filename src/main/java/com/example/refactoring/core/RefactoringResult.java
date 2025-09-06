package com.example.refactoring.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 重构结果类
 */
public class RefactoringResult {
    
    private final boolean success;
    private final String message;
    private final List<String> warnings;
    private final List<String> modifiedFiles;
    
    private RefactoringResult(boolean success, String message, 
                             List<String> warnings, List<String> modifiedFiles) {
        this.success = success;
        this.message = message;
        this.warnings = warnings != null ? warnings : new ArrayList<>();
        this.modifiedFiles = modifiedFiles != null ? modifiedFiles : new ArrayList<>();
    }
    
    public static RefactoringResult success(String message, List<String> modifiedFiles) {
        return new RefactoringResult(true, message, null, modifiedFiles);
    }
    
    public static RefactoringResult success(String message, List<String> modifiedFiles, List<String> warnings) {
        return new RefactoringResult(true, message, warnings, modifiedFiles);
    }
    
    public static RefactoringResult failure(String message) {
        return new RefactoringResult(false, message, null, null);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }
    
    public List<String> getModifiedFiles() {
        return new ArrayList<>(modifiedFiles);
    }
    
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RefactoringResult{")
          .append("success=").append(success)
          .append(", message='").append(message).append('\'');
        
        if (!warnings.isEmpty()) {
            sb.append(", warnings=").append(warnings);
        }
        
        if (!modifiedFiles.isEmpty()) {
            sb.append(", modifiedFiles=").append(modifiedFiles);
        }
        
        sb.append('}');
        return sb.toString();
    }
}
