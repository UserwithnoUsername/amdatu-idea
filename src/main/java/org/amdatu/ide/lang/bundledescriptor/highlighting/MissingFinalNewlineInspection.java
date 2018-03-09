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
package org.amdatu.ide.lang.bundledescriptor.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.amdatu.ide.lang.bundledescriptor.BundleDescriptorBundle;
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorFile;
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorTokenType;
import org.amdatu.ide.lang.bundledescriptor.psi.Header;
import org.amdatu.ide.lang.bundledescriptor.psi.Section;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class MissingFinalNewlineInspection extends LocalInspectionTool {
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
                    boolean isOnTheFly) {
        if (file instanceof BundleDescriptorFile) {
            String text = file.getText();
            if (text != null && text.length() > 0 && !StringUtil.endsWith(text, "\n")) {
                List<Section> sections = ((BundleDescriptorFile) file).getSections();
                assert sections.size() > 0 : text;
                Section section = sections.get(sections.size() - 1);
                ProblemDescriptor descriptor = manager.createProblemDescriptor(
                                section.getLastChild(), BundleDescriptorBundle.message("inspection.newline.message"),
                                new AddNewlineQuickFix(section), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                isOnTheFly
                );
                return new ProblemDescriptor[] { descriptor };
            }
        }

        return null;
    }

    private static class AddNewlineQuickFix extends AbstractManifestQuickFix {
        private AddNewlineQuickFix(Section section) {
            super(section);
        }

        @NotNull
        @Override
        public String getText() {
            return BundleDescriptorBundle.message("inspection.newline.fix");
        }

        @Override
        public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement,
                        @NotNull PsiElement endElement) {
            PsiElement lastChild = startElement.getLastChild();
            if (lastChild instanceof Header) {
                lastChild.getNode().addLeaf(BundleDescriptorTokenType.NEWLINE, "\n", null);
            }
        }
    }
}
