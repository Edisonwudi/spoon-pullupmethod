package com.example.refactoring.core;

/**
 * 重构过程中的异常类
 */
public class RefactoringException extends Exception {
    
    public RefactoringException(String message) {
        super(message);
    }
    
    public RefactoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
