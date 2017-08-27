package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;

public class MethodParameters implements SmartFoldingBuilder.Builder {

    @NotNull
    @Override
    public Stream<NamedFoldingDescriptor> buildFoldRegions(@NotNull PsiElement root, @NotNull Document document) {
        return findChildrenOfType(root, PsiParameterList.class).stream()
                .filter(e -> e.getParent() instanceof PsiMethod)
                .filter(e -> e.getParameters().length > 0)
                .filter(e -> !((PsiMethod) e.getParent()).hasModifierProperty(PsiModifier.ABSTRACT))
                .flatMap(e -> collapse(e, document));
    }

    private Stream<NamedFoldingDescriptor> collapse(@NotNull PsiParameterList parameter, @NotNull Document document) {
        FoldingGroup group = FoldingGroup.newGroup("method");
        return Arrays.stream(parameter.getParameters())
                        .flatMap(e -> collapse(e, group, document));
    }

    private Stream<NamedFoldingDescriptor> collapse(@NotNull PsiParameter parameter,
                                               @NotNull FoldingGroup group,
                                               @NotNull Document document) {
        if (parameter.getModifierList() == null)
            return Stream.empty();

        if (parameter.hasModifierProperty(PsiModifier.FINAL)) {
            return Arrays.stream(parameter.getModifierList().getChildren())
                    .filter(e -> PsiModifier.FINAL.equals(e.getText()))
                    .map(e -> e.getTextRange().grown(1))
                    .map(e -> new NamedFoldingDescriptor(parameter.getNode(), e, group, ""));
        } else {
            // IntelliJ IDEA will not collapse empty regions, so we take the previous character
            int startOffset = parameter.getModifierList().getTextOffset() - 1;
            int endOffset = startOffset + 1;
            TextRange range = new TextRange(startOffset, endOffset);
            String shortcut = document.getText(range) + "var ";
            return Stream.of(new NamedFoldingDescriptor(parameter.getNode(), range, group, shortcut));
        }
    }
}
