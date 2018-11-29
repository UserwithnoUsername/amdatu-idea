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

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPackage
import com.intellij.psi.util.PsiTreeUtil
import org.amdatu.idea.BaseliningErrorService
import org.amdatu.idea.inspections.quickfix.UpdatePackageInfoJavaPackageVersion

class PackageInfoJavaPackageVersion : LocalInspectionTool() {

    override fun getDisplayName(): String {
        return "Package version baselining error (package-info.java)"
    }

    override fun getStaticDescription(): String? {
        return """
            This inspection checks if a package version needs to be bumped based according to semantic versioning rules
        """.trimIndent()
    }

    override fun getGroupDisplayName(): String {
        return "Amdatu"
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.name != PsiPackage.PACKAGE_INFO_FILE) {
            return null
        }

        val baseliningErrorService = file.project.getComponent(BaseliningErrorService::class.java)

        val packageSuggestion = baseliningErrorService.getPackageSuggestion(file.virtualFile) ?: return null

        val versionAnnotation = PsiTreeUtil.collectElementsOfType(file, PsiAnnotation::class.java)
                .firstOrNull { it.qualifiedName == "org.osgi.annotation.versioning.Version" }

        if (versionAnnotation != null && versionAnnotation.findAttributeValue(null)?.text ==  "\"${packageSuggestion.suggestedVersion}\"") {
            return null // Already using suggested version
        }

        val any:PsiElement = versionAnnotation ?: file
        val problemDescriptor = manager.createProblemDescriptor(any,
                "Bundle version too low should be ${packageSuggestion.suggestedVersion}",
                isOnTheFly,
                arrayOf(UpdatePackageInfoJavaPackageVersion(packageSuggestion)),
                ProblemHighlightType.ERROR)
        return arrayOf(problemDescriptor)
    }
}
