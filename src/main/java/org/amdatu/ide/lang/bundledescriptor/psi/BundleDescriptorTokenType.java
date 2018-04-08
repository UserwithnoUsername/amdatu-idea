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
package org.amdatu.ide.lang.bundledescriptor.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILeafElementType;
import org.amdatu.ide.lang.bundledescriptor.BundleDescriptorLanguage;
import org.amdatu.ide.lang.bundledescriptor.psi.impl.BundleDescriptorTokenImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public final class BundleDescriptorTokenType extends IElementType implements ILeafElementType {
    public static final BundleDescriptorTokenType
                    HEADER_NAME = new BundleDescriptorTokenType("HEADER_NAME_TOKEN");
    public static final BundleDescriptorTokenType
                    NEWLINE = new BundleDescriptorTokenType("NEWLINE_TOKEN");
    public static final BundleDescriptorTokenType
                    SECTION_END = new BundleDescriptorTokenType("SECTION_END_TOKEN");
    public static final BundleDescriptorTokenType
                    COLON = new BundleDescriptorTokenType("COLON_TOKEN");
    public static final BundleDescriptorTokenType
                    SEMICOLON = new BundleDescriptorTokenType("SEMICOLON_TOKEN");
    public static final BundleDescriptorTokenType
                    EQUALS = new BundleDescriptorTokenType("EQUALS_TOKEN");
    public static final BundleDescriptorTokenType
                    COMMA = new BundleDescriptorTokenType("COMMA_TOKEN");
    public static final BundleDescriptorTokenType
                    QUOTE = new BundleDescriptorTokenType("QUOTE_TOKEN");
    public static final BundleDescriptorTokenType
                    HEADER_VALUE_PART = new BundleDescriptorTokenType("HEADER_VALUE_PART_TOKEN");
    public static final BundleDescriptorTokenType
                    SIGNIFICANT_SPACE = new BundleDescriptorTokenType("SIGNIFICANT_SPACE_TOKEN");
    public static final BundleDescriptorTokenType
                    OPENING_PARENTHESIS_TOKEN = new BundleDescriptorTokenType("OPENING_PARENTHESIS_TOKEN");
    public static final BundleDescriptorTokenType
                    CLOSING_PARENTHESIS_TOKEN = new BundleDescriptorTokenType("CLOSING_PARENTHESIS_TOKEN");
    public static final BundleDescriptorTokenType
                    OPENING_BRACKET_TOKEN = new BundleDescriptorTokenType("OPENING_BRACKET_TOKEN");
    public static final BundleDescriptorTokenType
                    CLOSING_BRACKET_TOKEN = new BundleDescriptorTokenType("CLOSING_BRACKET_TOKEN");
    public static final BundleDescriptorTokenType
                    COMMENT = new BundleDescriptorTokenType("COMMENT");

    private BundleDescriptorTokenType(@NotNull @NonNls String debugName) {
        super(debugName, BundleDescriptorLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public ASTNode createLeafNode(@NotNull CharSequence text) {
        return new BundleDescriptorTokenImpl(this, text);
    }
}
