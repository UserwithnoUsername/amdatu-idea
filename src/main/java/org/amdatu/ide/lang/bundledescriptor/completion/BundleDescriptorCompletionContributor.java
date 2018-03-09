/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.amdatu.ide.lang.bundledescriptor.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.amdatu.ide.lang.bundledescriptor.BundleDescriptorLanguage;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParserRepository;
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * Completion contributor which adds the name of all known headers to the autocomplete list.
 *
 * @author <a href="mailto:janthomae@janthomae.de">Jan Thom&auml;</a>
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class BundleDescriptorCompletionContributor extends CompletionContributor {
    public BundleDescriptorCompletionContributor(@NotNull final HeaderParserRepository repository) {
        extend(CompletionType.BASIC,
                        PlatformPatterns.psiElement(BundleDescriptorTokenType.HEADER_NAME)
                                        .withLanguage(BundleDescriptorLanguage.INSTANCE),
                        new CompletionProvider<CompletionParameters>() {
                            @Override
                            public void addCompletions(@NotNull CompletionParameters parameters,
                                            ProcessingContext context,
                                            @NotNull CompletionResultSet resultSet) {
                                for (String header : repository.getAllHeaderNames()) {
                                    resultSet.addElement(LookupElementBuilder.create(header)
                                                    .withInsertHandler(HEADER_INSERT_HANDLER));
                                }
                            }
                        }
        );
    }

    private static final InsertHandler<LookupElement> HEADER_INSERT_HANDLER = new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
            context.setAddCompletionChar(false);
            EditorModificationUtil.insertStringAtCaret(context.getEditor(), ": ");
            context.commitDocument();
        }
    };
}