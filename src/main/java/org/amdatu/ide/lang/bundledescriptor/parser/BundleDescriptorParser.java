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
package org.amdatu.ide.lang.bundledescriptor.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.amdatu.ide.lang.bundledescriptor.BundleDescriptorBundle;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParser;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParserRepository;
import org.amdatu.ide.lang.bundledescriptor.header.impl.StandardHeaderParser;
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorElementType;
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorTokenType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class BundleDescriptorParser implements PsiParser {
    public static final TokenSet HEADER_END_TOKENS =
                    TokenSet.create(BundleDescriptorTokenType.SECTION_END, BundleDescriptorTokenType.HEADER_NAME);

    private final HeaderParserRepository myRepository;

    public BundleDescriptorParser() {
        myRepository = ServiceManager.getService(HeaderParserRepository.class);
    }

    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        builder.setDebugMode(ApplicationManager.getApplication().isUnitTestMode());

        PsiBuilder.Marker rootMarker = builder.mark();
        while (!builder.eof()) {
            parseSection(builder);
        }
        rootMarker.done(root);

        return builder.getTreeBuilt();
    }

    private void parseSection(PsiBuilder builder) {
        PsiBuilder.Marker section = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();
            if (tokenType == BundleDescriptorTokenType.HEADER_NAME) {
                parseHeader(builder);
            }
            else if (tokenType == BundleDescriptorTokenType.SECTION_END) {
                builder.advanceLexer();
                break;
            }
            else {
                PsiBuilder.Marker marker = builder.mark();
                consumeHeaderValue(builder);
                marker.error(BundleDescriptorBundle.message("manifest.header.expected"));
            }
        }

        section.done(BundleDescriptorElementType.SECTION);
    }

    private void parseHeader(PsiBuilder builder) {
        PsiBuilder.Marker header = builder.mark();
        String headerName = builder.getTokenText();
        assert headerName != null : "[" + builder.getOriginalText() + "]@" + builder.getCurrentOffset();
        builder.advanceLexer();

        if (builder.getTokenType() == BundleDescriptorTokenType.COLON) {
            builder.advanceLexer();

            if (!expect(builder, BundleDescriptorTokenType.SIGNIFICANT_SPACE)) {
                // This is not an error in a bnd descriptor
//                builder.error(BundleDescriptorBundle.message("manifest.whitespace.expected"));
            }

            HeaderParser headerParser =
                            notNull(myRepository.getHeaderParser(headerName), StandardHeaderParser.INSTANCE);
            headerParser.parse(builder);
        }
        else {
            PsiBuilder.Marker marker = builder.mark();
            consumeHeaderValue(builder);
            marker.error(BundleDescriptorBundle.message("manifest.colon.expected"));
        }

        header.done(BundleDescriptorElementType.HEADER);
    }

    private static void consumeHeaderValue(PsiBuilder builder) {
        while (!builder.eof() && !HEADER_END_TOKENS.contains(builder.getTokenType())) {
            builder.advanceLexer();
        }
    }
}
