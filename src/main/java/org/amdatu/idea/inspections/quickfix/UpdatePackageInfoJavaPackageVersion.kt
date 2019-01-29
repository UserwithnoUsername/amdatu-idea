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

package org.amdatu.idea.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.amdatu.idea.BaseliningPackageSuggestion
import java.io.IOException

class UpdatePackageInfoJavaPackageVersion(val baseliningPackageSuggestion: BaseliningPackageSuggestion) : LocalQuickFix {

    init {
        if (!baseliningPackageSuggestion.source.name.equals("package-info.java")) {
            throw IllegalArgumentException("UpdatePackageInfoPackageVersion can only be used on package-info.java")
        }
    }

    override fun getName(): String {
        return "Update package version to ${baseliningPackageSuggestion.suggestedVersion}"
    }

    override fun getFamilyName(): String {
        return "Amdatu"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        apply()
    }

    fun apply() {
        try {
            val contents = baseliningPackageSuggestion.source.contentsToByteArray()
            var contentAsString = String(contents)
            contentAsString = contentAsString.replace("\\(.*\\)".toRegex(), "(\"${baseliningPackageSuggestion.suggestedVersion}\")")
            baseliningPackageSuggestion.source.setBinaryContent(contentAsString.toByteArray())
        } catch (e: IOException) {
            throw RuntimeException("Could not write to package-info.java", e)
        }
    }
}