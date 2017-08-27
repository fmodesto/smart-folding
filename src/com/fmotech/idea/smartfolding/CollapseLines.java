package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.twelvemonkeys.lang.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.text.StringUtil.containsLineBreak;

public class CollapseLines {

    // whitespace* newline whitespace*
    private static final String NEW_LINES = "([^\\S\\n]*\n[^\\S\\n]*)+";
    private static final Pattern NEW_LINES_PATTERN = Pattern.compile(NEW_LINES);

    public FoldingDescriptor[] process(List<NamedFoldingDescriptor> foldings, Document document) {
        Map<PsiElement, List<NamedFoldingDescriptor>> map = foldings.stream()
                .filter(e -> findParent(e) != null)
                .collect(Collectors.groupingBy(this::findParent));

        return map.entrySet().stream()
                .flatMap(e -> processNewLines(e.getKey(), e.getValue(), document))
                .toArray(FoldingDescriptor[]::new);
    }

    private PsiElement findParent(NamedFoldingDescriptor descriptor) {
        PsiElement element = descriptor.getElement().getPsi();
        while (element != null && !collapsableElement(element)) {
            element = element.getParent();
        }
        return element;
    }

    private boolean collapsableElement(PsiElement element) {
        return element instanceof PsiParameterList
                || element instanceof PsiStatement;
    }

    private Stream<NamedFoldingDescriptor> processNewLines(
            PsiElement element, List<NamedFoldingDescriptor> foldings, Document document) {
        String text = document.getText(element.getTextRange());
        if (!containsLineBreak(text) || StringUtil.contains(text, '}'))
            return foldings.stream();

        int diffSize = foldings.stream()
                .mapToInt(e -> e.getRange().getLength() - e.getPlaceholderText().length())
                .sum();

        final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(element.getProject());
        String shortText = text.replaceAll(NEW_LINES, " ");
        if (shortText.length() - diffSize > settings.getRightMargin(JavaLanguage.INSTANCE))
            return foldings.stream();

        Matcher matcher = NEW_LINES_PATTERN.matcher(text);
        FoldingGroup group = FoldingGroup.newGroup("newlines");
        while (matcher.find()) {
            int startOffset = element.getTextOffset() + matcher.start();
            // Dirty fix to avoid collapsing elements which might collapse with 'var'
            int endOffset = element.getTextOffset() + matcher.end() - 1;
            foldings.add(new NamedFoldingDescriptor(element, startOffset, endOffset, group, " "));
        }

        return foldings.stream();
    }
}
