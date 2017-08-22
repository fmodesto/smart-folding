package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeElement;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;

public class FullyQualifiedNames extends SmartFoldingBuilder {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        return findChildrenOfType(root, PsiTypeElement.class).stream()
                .filter(e -> e.getText().contains("."))
                .map(this::collapse)
                .toArray(FoldingDescriptor[]::new);
    }

    private FoldingDescriptor collapse(PsiTypeElement type) {
        String name = type.getText();
        int length = name.lastIndexOf('.');
        int startOffset = type.getTextOffset();
        int endOffset = startOffset + length + 1;
        String shortcut = Arrays.stream(name.substring(0, length).split("\\."))
                .map(e -> String.valueOf(e.charAt(0)))
                .collect(Collectors.joining(".")) + ".";
        return new NamedFoldingDescriptor(type, startOffset, endOffset, FoldingGroup.newGroup("qualified"), shortcut);
    }
}
