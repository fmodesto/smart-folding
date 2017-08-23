package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.psi.util.PsiTreeUtil.findChildOfType;
import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;

public class FullyQualifiedNames extends SmartFoldingBuilder {

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        return Stream.concat(findChildrenOfType(root, PsiTypeElement.class).stream()
                                .map(e -> findChildOfType(e, PsiJavaCodeReferenceElement.class)),
                             findChildrenOfType(root, PsiNewExpression.class).stream()
                                .map(e -> findChildOfType(e, PsiJavaCodeReferenceElement.class)))
                .filter(Objects::nonNull)
                .filter(e -> e.getText().startsWith(e.getQualifiedName()))
                .filter(e -> e.getQualifiedName().contains("."))
                .map(this::collapse)
                .toArray(FoldingDescriptor[]::new);
    }

    private FoldingDescriptor collapse(PsiJavaCodeReferenceElement type) {
        PsiJavaCodeReferenceElement target = type;
        while (target.getChildren().length > 0 && target.getChildren()[0] instanceof PsiJavaCodeReferenceElement) {
            target = (PsiJavaCodeReferenceElement) target.getChildren()[0];
        }
        String name = type.getText();
        String qualified = type.getQualifiedName();
        int length = qualified.lastIndexOf('.');
        int startOffset = target.getTextOffset();
        int endOffset = startOffset + length + 1;
        String shortcut = Arrays.stream(name.substring(0, length).split("\\."))
                .map(e -> String.valueOf(e.charAt(0)))
                .collect(Collectors.joining()) + ".";
        return new NamedFoldingDescriptor(target, startOffset, endOffset, FoldingGroup.newGroup("qualified"), shortcut);
    }
}
