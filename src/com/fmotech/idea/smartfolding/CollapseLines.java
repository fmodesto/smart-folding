package com.fmotech.idea.smartfolding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;

public class CollapseLines {

    // whitespace* newline whitespace*
    private static final String NEW_LINES = "([^\\S\\n]*\n[^\\S\\n]*)+";
    private static final Pattern NEW_LINES_PATTERN = Pattern.compile(NEW_LINES);

    public FoldingDescriptor[] process(List<NamedFoldingDescriptor> foldings, Document document) {
        Map<PsiElement, List<NamedFoldingDescriptor>> map = foldings.stream()
                .filter(e -> findParent(e) != null)
                .collect(groupingBy(this::findParent));

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
            final PsiElement element, final List<NamedFoldingDescriptor> foldings, final Document document) {
        TextRange range = element.getTextRange();
        if (document.getLineNumber(range.getStartOffset()) == document.getLineNumber(range.getEndOffset())) {
            return foldings.stream();
        } else if (element instanceof PsiParameterList) {
            return processParameterList(element, foldings, document);
        } else if (element instanceof PsiStatement) {
            return processDeclaration(element, foldings, document);
        } else {
            return foldings.stream();
        }
    }

    private Stream<NamedFoldingDescriptor> processParameterList(
            final PsiElement element, final List<NamedFoldingDescriptor> foldings, final Document document) {
        int startOffset = document.getLineStartOffset(document.getLineNumber(element.getTextOffset()));
        int endOffset = document.getLineEndOffset(document.getLineNumber(element.getTextRange().getEndOffset()));
        String text = document.getCharsSequence().subSequence(startOffset, endOffset).toString();

        int diffSize = foldings.stream()
                .mapToInt(e -> computeDiff(e))
                .sum();

        String shortText = text.replaceAll(NEW_LINES, " ");
        if (shortText.length() - diffSize > getRightMargin(element))
            return foldings.stream();

        Matcher matcher = NEW_LINES_PATTERN.matcher(text);
        FoldingGroup group = FoldingGroup.newGroup("newlines");
        while (matcher.find()) {
            TextRange range = TextRange.create(startOffset + matcher.start(), startOffset + matcher.end());
            if (isRangeAlreadyCollapsed(foldings, range)) {
                foldings.add(new NamedFoldingDescriptor(element.getNode(), range.grown(-1), group, ""));
            } else {
                foldings.add(new NamedFoldingDescriptor(element.getNode(), range, group, " "));
            }
        }
        return foldings.stream();
    }

    private boolean isRangeAlreadyCollapsed(List<NamedFoldingDescriptor> foldings, TextRange range) {
        return foldings.stream().anyMatch(e -> e.getRange().intersects(range));
    }

    private Stream<NamedFoldingDescriptor> processDeclaration(
            final PsiElement element, final List<NamedFoldingDescriptor> foldings, final Document document) {
        int startLine = document.getLineNumber(element.getTextOffset());
        int endLine = document.getLineNumber(element.getTextRange().getEndOffset());
        int startOffset = document.getLineStartOffset(startLine);

        String text = document.getCharsSequence().subSequence(
                document.getLineStartOffset(startLine), document.getLineEndOffset(endLine)).toString();
        Map<Integer, Integer> byLine = foldings.stream()
                .collect(groupingBy(e -> document.getLineNumber(e.getRange().getStartOffset()),
                        summingInt(this::computeDiff)));
        String[] lines = StringUtil.splitByLinesDontTrim(text);
        int[] length = Arrays.stream(lines)
                .mapToInt(String::length)
                .toArray();
        int[] blanks = Arrays.stream(lines)
                .mapToInt(e -> e.length() - e.trim().length())
                .toArray();

        int rightMargin = getRightMargin(element);
        Matcher matcher = NEW_LINES_PATTERN.matcher(text);
        FoldingGroup group = FoldingGroup.newGroup("newlines");
        while (matcher.find()) {
            TextRange range = TextRange.create(startOffset + matcher.start(), startOffset + matcher.end());
            int line = document.getLineNumber(range.getStartOffset());
            // TODO Find a better way to ignore stream alignment
            if (!lines[line + 1 - startLine].matches("\\s+\\..*")) {
                int totalLength = length[line - startLine] + length[line + 1 - startLine];
                int reductionByFolding = byLine.getOrDefault(line, 0) + byLine.getOrDefault(line + 1, 0);
                int reductionByBlank = blanks[line + 1 - startLine];
                if (totalLength - reductionByFolding - reductionByBlank <= rightMargin) {
                    foldings.add(new NamedFoldingDescriptor(element.getNode(), range, group, " "));
                    length[line + 1 - startLine] = totalLength - reductionByFolding - reductionByBlank;
                    blanks[line + 1 - startLine] = 0;
                    byLine.put(line + 1, 0);
                }
            }
        }
        return foldings.stream();
    }

    private int getRightMargin(PsiElement element) {
        final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(element.getProject());
        return settings.getRightMargin(JavaLanguage.INSTANCE);
    }

    private int computeDiff(NamedFoldingDescriptor e) {
        return e.getRange().getLength() - e.getPlaceholderText().length();
    }
}
