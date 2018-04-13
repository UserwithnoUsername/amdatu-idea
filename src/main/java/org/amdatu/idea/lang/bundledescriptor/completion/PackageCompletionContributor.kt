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

package org.amdatu.idea.lang.bundledescriptor.completion

import aQute.bnd.header.Parameters
import aQute.bnd.osgi.Constants
import aQute.lib.utf8properties.UTF8Properties
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.amdatu.idea.inspections.PackageUtil
import org.amdatu.idea.lang.bundledescriptor.psi.BundleDescriptorTokenType
import org.amdatu.idea.lang.bundledescriptor.psi.Header


class PackageCompletionContributor : CompletionContributor() {

    private fun getPlace(name: String): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                .withSuperParent(2, PlatformPatterns.psiElement<Header>(Header::class.java)
                        .withName(StandardPatterns.string().startsWith(name)))
    }

    init {
        val completionProvider = PackageCompletionProvider()
        extend(CompletionType.BASIC, getPlace(Constants.EXPORT_PACKAGE), completionProvider)
        extend(CompletionType.BASIC, getPlace(Constants.PRIVATEPACKAGE), completionProvider)
        extend(CompletionType.BASIC, getPlace(Constants.PRIVATE_PACKAGE), completionProvider)
    }

    class PackageCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            if (shouldComplete(parameters)) return

            // Use bnd's UTF8Properties to read the contents of the full instruction, this properties implementation
            // strips some characters like the newlines escaped with a '\\'
            val p = UTF8Properties()
            p.load("$DUMMY_PROPERTY_KEY: ${parameters.position.parent.text}", null, null)

            //val file: VirtualFile, val workspace: Workspace, private val builder: Builder, val module: Module

            val project = parameters.position.project
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            val module = projectFileIndex.getModuleForFile(parameters.originalFile.virtualFile) ?: return

            val added = Parameters(p[DUMMY_PROPERTY_KEY] as String).keys
            PackageUtil.getPsiPackagesForModule(module)
                    .filter { !added.contains(it.qualifiedName) } // Remove already added bundles
                    .forEach {
                        result.addElement(LookupElementBuilder.create(it.qualifiedName))
                    }
        }

        private fun shouldComplete(parameters: CompletionParameters): Boolean {
            // Check if completion should be available based on current position
            // Only provide options in case a this is
            // - the first element
            // - a comma was added after the previous element
            var prevSibling: PsiElement? = parameters.position.prevSibling
            while (prevSibling != null) {
                if (BundleDescriptorTokenType.COMMA == prevSibling.node.elementType) {
                    break
                } else if (BundleDescriptorTokenType.HEADER_VALUE_PART == prevSibling.node.elementType) {
                    if (prevSibling.node.text != "\\") {
                        return true
                    }
                }

                prevSibling = prevSibling.prevSibling
            }
            return false
        }

    }
}