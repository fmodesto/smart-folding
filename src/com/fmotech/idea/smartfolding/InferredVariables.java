package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.editor.FoldingGroup.newGroup;
import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;

public class InferredVariables extends SmartFoldingBuilder {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        return Stream.concat(
                findChildrenOfType(root, PsiDeclarationStatement.class).stream()
                        .filter(e -> e.getDeclaredElements().length > 0)
                        .filter(e -> e.getDeclaredElements()[0] instanceof PsiLocalVariable)
                        .map(e -> (PsiVariable) e.getDeclaredElements()[0])
                        .filter(PsiVariable::hasInitializer),
                findChildrenOfType(root, PsiForeachStatement.class).stream()
                        .map(e -> (PsiVariable) e.getIterationParameter()))
                .filter(e -> Objects.nonNull(e.getNameIdentifier()))
                .flatMap(e -> collapse(e, document))
                .toArray(FoldingDescriptor[]::new);
    }

    private Stream<FoldingDescriptor> collapse(PsiVariable variable, Document document) {
        String shortcut = variable.hasModifierProperty(PsiModifier.FINAL) ? "let" : "var";
        int startOffset = variable.getTextRange().getStartOffset();
        int endOffset = variable.getNameIdentifier().getTextRange().getStartOffset() - 1;
        FoldingGroup group = newGroup("final");
        return Stream.concat(
                Stream.of(new NamedFoldingDescriptor(variable.getNode(), startOffset, endOffset, group, shortcut)),
                foldNewLines(variable, document, group));
    }

    private Stream<FoldingDescriptor> foldNewLines(PsiVariable variable, Document document, FoldingGroup group) {
        if (variable.getInitializer() == null)
            return Stream.empty();

        int startLine = document.getLineNumber(variable.getTextOffset());
        int endLine = document.getLineNumber(variable.getInitializer().getTextOffset());
        if (startLine == endLine)
            return Stream.empty();

        int startOffset = document.getLineEndOffset(startLine);
        int endOffset = variable.getInitializer().getTextOffset();
        String text = document.getText(new TextRange(startOffset, endOffset));
        if (!text.matches("\\s*=?\\s*"))
            return Stream.empty();

        String shortcut = text.contains("=") ? " = " : " ";
        return Stream.of(new NamedFoldingDescriptor(variable.getNode(), startOffset, endOffset, group, shortcut));
    }
}
