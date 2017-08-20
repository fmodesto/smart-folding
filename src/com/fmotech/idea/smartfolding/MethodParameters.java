package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;

public class MethodParameters extends SmartFoldingBuilder {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        return findChildrenOfType(root, PsiParameterList.class).stream()
                .filter(e -> !(e.getParent() instanceof PsiLambdaExpression))
                .flatMap(e -> collapse(e, document))
                .toArray(FoldingDescriptor[]::new);
    }

    @Nullable
    private Stream<FoldingDescriptor> collapse(PsiParameterList parameter, Document document) {
        FoldingGroup group = FoldingGroup.newGroup("method");
        return Arrays.stream(parameter.getParameters())
                .flatMap(e -> collapse(e, group, document));
    }

    @Nullable
    private Stream<FoldingDescriptor> collapse(PsiParameter parameter, FoldingGroup group, Document document) {
        if (parameter.getModifierList() == null)
            return Stream.empty();

        if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
            return Arrays.stream(parameter.getModifierList().getChildren())
                    .filter(e -> PsiModifier.FINAL.equals(e.getText()))
                    .map(e -> e.getTextRange().grown(1))
                    .map(e -> new NamedFoldingDescriptor(parameter.getNode(), e, group, ""));
        } else {
            int startOffset = parameter.getModifierList().getTextOffset() - 1;
            int endOffset = startOffset + 1;
            TextRange range = new TextRange(startOffset, endOffset);
            String shortcut = document.getText(range) + "var ";
            return Stream.of(new NamedFoldingDescriptor(parameter.getNode(), range, group, shortcut));
        }
    }
}
