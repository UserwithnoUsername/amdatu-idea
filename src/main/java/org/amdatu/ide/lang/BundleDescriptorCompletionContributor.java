package org.amdatu.ide.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.ManifestLanguage;
import org.jetbrains.lang.manifest.psi.ManifestTokenType;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

import aQute.bnd.osgi.Constants;

public class BundleDescriptorCompletionContributor extends CompletionContributor {

    public BundleDescriptorCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(ManifestTokenType.HEADER_NAME).withLanguage(ManifestLanguage.INSTANCE),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    public void addCompletions(@NotNull CompletionParameters parameters,
                                               ProcessingContext context,
                                               @NotNull CompletionResultSet resultSet) {

                        // Override the default prefix matcher as the '-' char is removed from the prefix for the default matcher that's used
                        resultSet = resultSet.withPrefixMatcher(new PlainPrefixMatcher(findPrefix(parameters)));

                        for (String header : Constants.headers) {
                            resultSet.addElement(LookupElementBuilder.create(header).withInsertHandler(HEADER_INSERT_HANDLER));
                        }

                        for (String header : Constants.options) {
                            resultSet.addElement(LookupElementBuilder.create(header).withInsertHandler(HEADER_INSERT_HANDLER));
                        }
                    }
                }
        );
    }
    public static String findPrefix(@NotNull CompletionParameters parameters) {
        final PsiElement position = parameters.getPosition();
        final int offset = parameters.getOffset();
        TextRange range = position.getTextRange();
        assert range.containsOffset(offset) : position + "; " + offset + " not in " + range;
        //noinspection deprecation

        String substr = position.getText().substring(0, offset - position.getTextRange().getStartOffset());
        if (substr.length() == 0 || Character.isWhitespace(substr.charAt(substr.length() - 1))) return "";

        substr = substr.trim();

        return substr.trim();
    }

    private static final InsertHandler<LookupElement> HEADER_INSERT_HANDLER = (context, item) -> {
        context.setAddCompletionChar(false);
        EditorModificationUtil.insertStringAtCaret(context.getEditor(), ": ");
        context.commitDocument();
    };
}
