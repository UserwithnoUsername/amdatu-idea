package org.amdatu.ide.lang.bundledescriptor.completion

import aQute.bnd.header.Parameters
import aQute.bnd.osgi.Constants
import aQute.lib.utf8properties.UTF8Properties
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.amdatu.ide.AmdatuIdePlugin
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorTokenType
import org.amdatu.ide.lang.bundledescriptor.psi.Header

const val DUMMY_PROPERTY_KEY = "DummyProperty"

class BsnCompletionContributor : CompletionContributor() {

    private fun getPlace(name: String): PsiElementPattern.Capture<PsiElement> {
        return psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                .withSuperParent(2, psiElement<Header>(Header::class.java)
                        .withName(StandardPatterns.string().startsWith(name)))
    }

    init {
        extend(CompletionType.BASIC, getPlace(Constants.BUILDPATH), BundleCompletionProvider(true))
        extend(CompletionType.BASIC, getPlace(Constants.RUNBUNDLES), BundleCompletionProvider(false))
    }

    class BundleCompletionProvider(private val workspaceBundlesWithExportedPackagesOnly: Boolean) : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            if (shouldComplete(parameters)) return

            val amdatuIdePlugin = parameters.position.project.getComponent(AmdatuIdePlugin::class.java) ?: return

            // Use bnd's UTF8Properties to read the contents of the full instruction, this properties implementation
            // strips some characters like the newlines escaped with a '\\'
            val p = UTF8Properties()
            p.load("$DUMMY_PROPERTY_KEY: ${parameters.position.parent.text}", null, null)

            val added = Parameters(p[DUMMY_PROPERTY_KEY] as String).keys
            amdatuIdePlugin.workspace.allProjects
                    .flatMap {
                        it.getBuilder(null)
                                .subBuilders
                                .filter { !workspaceBundlesWithExportedPackagesOnly || it.exportPackage.isNotEmpty() } // Ignore bundles that don't export anything
                                .filter { !added.contains(it.bsn) } // Remove already added bundles
                                .map { "${it.bsn};version=latest" }

                    }
                    .forEach {
                        result.addElement(LookupElementBuilder.create(it))
                    }
            RepoUtil.getBundles(parameters.position.project).forEach {
                result.addElement(LookupElementBuilder.create(it))
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