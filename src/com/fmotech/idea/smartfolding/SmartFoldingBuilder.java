package com.fmotech.idea.smartfolding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SmartFoldingBuilder extends FoldingBuilderEx {

    interface Builder {
        Stream<NamedFoldingDescriptor> buildFoldRegions(@NotNull PsiElement root, @NotNull Document document);
    }

    private List<Builder> builders = Arrays.asList(new FullyQualifiedNames(), new InferredVariables(), new MethodParameters());
    private CollapseLines lines = new CollapseLines();

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<NamedFoldingDescriptor> foldings = builders.stream()
                .flatMap(e -> e.buildFoldRegions(root, document))
                .collect(Collectors.toList());

        return lines.process(foldings, document);
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }
}
