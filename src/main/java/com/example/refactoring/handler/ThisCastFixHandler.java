package com.example.refactoring.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;

/**
 * 修复方法上提后方法体内 this 的类型不匹配问题：
 * 若调用实参为 this，且对应参数类型不兼容祖先类型但兼容原后代类型，则替换为 (ChildClass) this。
 */
public class ThisCastFixHandler {

    private static final Logger logger = LoggerFactory.getLogger(ThisCastFixHandler.class);

    public void fixThisCastsForPulledUpMethod(CtMethod<?> method, CtClass<?> childClass, CtClass<?> parentClass) {
        try {
            if (method == null || method.getBody() == null) return;
            method.accept(new spoon.reflect.visitor.CtScanner() {
                @Override
                public <T> void visitCtInvocation(spoon.reflect.code.CtInvocation<T> invocation) {
                    tryFixArguments(invocation.getExecutable(), invocation.getArguments());
                    super.visitCtInvocation(invocation);
                }

                @Override
                public <T> void visitCtConstructorCall(spoon.reflect.code.CtConstructorCall<T> ctConstructorCall) {
                    tryFixArguments(ctConstructorCall.getExecutable(), ctConstructorCall.getArguments());
                    super.visitCtConstructorCall(ctConstructorCall);
                }

                private void tryFixArguments(spoon.reflect.reference.CtExecutableReference<?> execRef,
                                             java.util.List<spoon.reflect.code.CtExpression<?>> args) {
                    if (execRef == null || args == null || args.isEmpty()) return;
                    java.util.List<spoon.reflect.reference.CtTypeReference<?>> paramTypes = null;
                    try { paramTypes = execRef.getParameters(); } catch (Exception ignore) {}
                    if (paramTypes == null || paramTypes.size() != args.size()) return;

                    for (int i = 0; i < args.size(); i++) {
                        spoon.reflect.code.CtExpression<?> arg = args.get(i);
                        if (arg instanceof spoon.reflect.code.CtThisAccess) {
                            spoon.reflect.reference.CtTypeReference<?> expected = paramTypes.get(i);
                            if (expected == null) continue;
                            if (!isSubtypeOrSame(parentClass.getReference(), expected) &&
                                isSubtypeOrSame(childClass.getReference(), expected)) {
                                String castCode = "(" + childClass.getQualifiedName() + ") this";
                                spoon.reflect.code.CtExpression<?> casted = method.getFactory().Code().createCodeSnippetExpression(castCode);
                                args.set(i, casted);
                            }
                        }
                    }
                }

                private boolean isSubtypeOrSame(spoon.reflect.reference.CtTypeReference<?> a,
                                                spoon.reflect.reference.CtTypeReference<?> b) {
                    try {
                        if (a == null || b == null) return false;
                        return a.equals(b) || a.isSubtypeOf(b);
                    } catch (Exception ignore) {
                        return false;
                    }
                }
            });
        } catch (Exception e) {
            logger.debug("this 强制类型转换修复失败: {}", e.getMessage());
        }
    }
}


