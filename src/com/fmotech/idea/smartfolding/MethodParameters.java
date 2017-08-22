package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;

public class MethodParameters extends SmartFoldingBuilder {

    private static final String NEW_LINES = "([^\\S\\n]*\n[^\\S\\n]*)+";
    private static final Pattern NEW_LINES_PATTERN = Pattern.compile(NEW_LINES);

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        return findChildrenOfType(root, PsiParameterList.class).stream()
                .filter(e -> e.getParent() instanceof PsiMethod)
                .filter(e -> e.getParameters().length > 0)
                .filter(e -> !((PsiMethod) e.getParent()).hasModifierProperty(PsiModifier.ABSTRACT))
                .flatMap(e -> collapse(e, document))
                .toArray(FoldingDescriptor[]::new);
    }

    private Stream<FoldingDescriptor> collapse(@NotNull PsiParameterList parameter, @NotNull Document document) {
        FoldingGroup group = FoldingGroup.newGroup("method");
        return Stream.concat(
                Arrays.stream(parameter.getParameters())
                        .flatMap(e -> collapse(e, group, document)),
                collapseNewLines(parameter, group, document));
    }

    private Stream<FoldingDescriptor> collapse(@NotNull PsiParameter parameter,
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

    private Stream<FoldingDescriptor> collapseNewLines(@NotNull PsiParameterList parameters,
                                                       @NotNull FoldingGroup group,
                                                       @NotNull Document document) {
        PsiMethod method = (PsiMethod) parameters.getParent();
        if (method.getNameIdentifier() == null || method.getBody() == null)
            return Stream.empty();

        int lineName = document.getLineNumber(method.getNameIdentifier().getTextOffset());
        int lineStartParameters = document.getLineNumber(parameters.getParameters()[0].getTextOffset());
        int lineEndParameters = document.getLineNumber(parameters.getTextRange().getEndOffset());
        if (lineName != lineStartParameters || lineStartParameters == lineEndParameters)
            return Stream.empty();

        int startText = document.getLineStartOffset(lineName);
        int endText = document.getLineEndOffset(lineEndParameters);
        String text = document.getCharsSequence().subSequence(startText, endText).toString();
        String shorter = text.replaceAll("\\bfinal\\s", " ").replaceAll(NEW_LINES, " ");

        final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(parameters.getProject());
        if (shorter.length() > settings.getRightMargin(JavaLanguage.INSTANCE))
            return Stream.empty();

        List<FoldingDescriptor> descriptors = new ArrayList<>();
        Matcher matcher = NEW_LINES_PATTERN.matcher(text);
        while (matcher.find()) {
            int startOffset = startText + matcher.start();
            // Dirty fix to avoid collapsing elements which might collapse with 'var'
            int endOffset = startText + matcher.end() - 1;
            descriptors.add(new NamedFoldingDescriptor(parameters, startOffset, endOffset, group, " "));
        }
        return descriptors.stream();
    }
}
