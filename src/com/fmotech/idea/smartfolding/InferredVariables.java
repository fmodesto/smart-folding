package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

import static com.intellij.openapi.editor.FoldingGroup.newGroup;
import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;

public class InferredVariables implements SmartFoldingBuilder.Builder {

    @NotNull
    @Override
    public Stream<NamedFoldingDescriptor> buildFoldRegions(@NotNull PsiElement root, @NotNull Document document) {
        return Stream.concat(
                findChildrenOfType(root, PsiDeclarationStatement.class).stream()
                        .filter(e -> e.getDeclaredElements().length > 0)
                        .filter(e -> e.getDeclaredElements()[0] instanceof PsiLocalVariable)
                        .map(e -> (PsiVariable) e.getDeclaredElements()[0])
                        .filter(this::hasValidInitializer),
                findChildrenOfType(root, PsiForeachStatement.class).stream()
                        .map(e -> (PsiVariable) e.getIterationParameter()))
                .filter(e -> e.getNameIdentifier() != null)
                .map(this::collapse);
    }

    private boolean hasValidInitializer(PsiVariable variable) {
        return variable.hasInitializer() && !isInitializedToNull(variable);
    }

    private boolean isInitializedToNull(PsiVariable variable) {
        return variable.getInitializer() instanceof PsiLiteralExpression
                && "null".equals(variable.getInitializer().getText());
    }

    private NamedFoldingDescriptor collapse(PsiVariable variable) {
        String shortcut = variable.hasModifierProperty(PsiModifier.FINAL) ? "let" : "var";
        int startOffset = variable.getTextRange().getStartOffset();
        int endOffset = variable.getNameIdentifier().getTextRange().getStartOffset() - 1;
        FoldingGroup group = newGroup("final");
        return new NamedFoldingDescriptor(variable.getNode(), startOffset, endOffset, group, shortcut);
    }
}
