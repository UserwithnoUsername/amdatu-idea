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

import aQute.bnd.build.model.BndEditModel
import aQute.bnd.properties.Document
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.amdatu.idea.BaseliningBundleSuggestion
import java.io.IOException

class UpdateBundleVersion(private val baseliningBundleSuggestion: BaseliningBundleSuggestion) : LocalQuickFix {

    override fun getName(): String {
        return "Update Bundle-Version to ${baseliningBundleSuggestion.suggestedVersion}"
    }

    override fun getFamilyName(): String {
        return "Amdatu"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        apply()
    }

    fun apply() {
        try {
            val contents = baseliningBundleSuggestion.source.contentsToByteArray()
            val bndDocument = Document(String(contents))
            val bndEditModel = BndEditModel()
            bndEditModel.loadFrom(bndDocument)
            bndEditModel.setBundleVersion(baseliningBundleSuggestion.suggestedVersion)
            bndEditModel.saveChangesTo(bndDocument)
            baseliningBundleSuggestion.source.setBinaryContent(bndDocument.get().toByteArray())
        } catch (e: IOException) {
            throw RuntimeException("Could not edit bnd document " + baseliningBundleSuggestion.source.path, e)
        }
    }

}
