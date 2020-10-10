/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.amdatu.idea.inspections

import aQute.bnd.osgi.Constants
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.amdatu.idea.BaseliningErrorService
import org.amdatu.idea.inspections.quickfix.UpdateBundleVersion
import org.amdatu.idea.lang.bundledescriptor.psi.BundleDescriptorTokenType
import org.amdatu.idea.lang.bundledescriptor.psi.Header

class BundleVersionBaselining : LocalInspectionTool() {

    override fun getDisplayName(): String {
        return "Bundle version baselining error"
    }

    override fun getStaticDescription(): String? {
        return """
            This inspection checks if a bundle version needs to be bumped based according to semantic versioning rules
        """.trimIndent()
    }

    override fun getGroupDisplayName(): String {
        return "Amdatu"
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!file.name.endsWith(".bnd")) {
            return null
        }

        val baseliningErrorService = file.project.getComponent(BaseliningErrorService::class.java)

        val bundleSuggestion = baseliningErrorService.getBundleSuggestion(file.virtualFile) ?: return null

        val bundleVersionHeader = PlatformPatterns.psiElement(Header::class.java).withName(Constants.BUNDLE_VERSION)
        val addMarkerTo = PsiTreeUtil.collectElements(file, bundleVersionHeader::accepts).firstOrNull() ?: file

        val headerValuePartFinder = PlatformPatterns.psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
        val bundleVersionValue = PsiTreeUtil.collectElements(addMarkerTo, headerValuePartFinder::accepts).firstOrNull()
        if (bundleVersionValue?.text == bundleSuggestion.suggestedVersion) return null

        val problemDescriptor = manager.createProblemDescriptor(addMarkerTo,
                "Bundle version too low should be ${bundleSuggestion.suggestedVersion}",
                false,
                arrayOf(UpdateBundleVersion(bundleSuggestion)),
                ProblemHighlightType.ERROR)

        return arrayOf(problemDescriptor)
    }
}
