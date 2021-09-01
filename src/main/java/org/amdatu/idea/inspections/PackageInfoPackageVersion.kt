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
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import org.amdatu.idea.BaseliningErrorService
import org.amdatu.idea.inspections.quickfix.UpdatePackageInfoPackageVersion
import java.nio.charset.Charset

class PackageInfoPackageVersion : LocalInspectionTool() {

    override fun getDisplayName(): String {
        return "Package version baselining error (packageinfo)"
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
        if (file.name != "packageinfo") {
            return null
        }

        val baseliningErrorService = file.project.service<BaseliningErrorService>()

        val packageSuggestion = baseliningErrorService.getPackageSuggestion(file.virtualFile) ?: return null

        val contents = packageSuggestion.source.contentsToByteArray().toString(Charset.defaultCharset())
        val version = contents.lines()
                .filter { it.startsWith("version") }
                .map { it.substring("version".length).trim() }
                .firstOrNull()

        if (version == packageSuggestion.suggestedVersion) {
            return null // Already using suggested version
        }

        val problemDescriptor = manager.createProblemDescriptor(file,
                "Package version too low should be ${packageSuggestion.suggestedVersion}",
                isOnTheFly,
                arrayOf(UpdatePackageInfoPackageVersion(packageSuggestion)),
                ProblemHighlightType.ERROR)
        return arrayOf(problemDescriptor)
    }
}
