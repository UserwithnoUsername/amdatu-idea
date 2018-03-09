/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.amdatu.ide.lang.bundledescriptor.highlighting;

import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.psi.PsiElement;
import org.amdatu.ide.lang.bundledescriptor.BundleDescriptorBundle;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractManifestQuickFix extends LocalQuickFixOnPsiElement {
    protected AbstractManifestQuickFix(@NotNull PsiElement element) {
        super(element);
    }

    @NotNull
    @Override
    public final String getFamilyName() {
        return BundleDescriptorBundle.message("inspection.group");
    }
}