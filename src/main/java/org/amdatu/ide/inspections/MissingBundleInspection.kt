package org.amdatu.ide.inspections

import aQute.bnd.build.Project
import aQute.bnd.osgi.Constants
import aQute.bnd.service.Strategy
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.amdatu.ide.lang.bundledescriptor.completion.RepoUtil
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorTokenType
import org.amdatu.ide.lang.bundledescriptor.psi.Header

class MissingBundleInspection : LocalInspectionTool() {

    override fun getDisplayName(): String {
        return "Missing bundle"
    }

    override fun getStaticDescription(): String? {
        return """
            Inspection that reports issues when bundles that are used in -buildpath / -runbundles can't be found in a
             repository.

             It also raises a warning about bundles that are only available in the baseline repo, as this is often an
              indication the bundle was removed from the project and won't be available anymore after a release.

        """.trimIndent()
    }

    override fun getGroupDisplayName(): String {
        return "Amdatu Ide"
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!file.name.endsWith(".bnd")) {
            return null
        }

        val (_, module, _, bndProject, builder) =
                PsiUtil.getBndBuilderContextForPsiFile(file) ?: return null

        val onlyAvailableInBaselineRepo = RepoUtil.getBundlesOnlyAvailableInBaselineRepo(file.project)


        return listOf(Constants.BUILDPATH, Constants.RUNBUNDLES)
                .flatMap {  createProblemDescriptors(it, file, bndProject, onlyAvailableInBaselineRepo, manager) }
                .toTypedArray()
    }

    private fun createProblemDescriptors(headerName: String, file: PsiFile, bndProject: Project, onlyAvailableInBaselineRepo: Set<String>, manager: InspectionManager): List<ProblemDescriptor> {
        val header = PlatformPatterns.psiElement<Header>(Header::class.java).withName(headerName)
        val headerPsi = PsiTreeUtil.collectElements(file, header::accepts).firstOrNull() ?: file

        return bndProject.getBundles(Strategy.LOWEST, bndProject.mergeProperties(headerName), headerName)
                .filter { it.error != null || onlyAvailableInBaselineRepo.contains(it.bundleSymbolicName) }
                .map { container ->
                    val headerValuePartFinder = PlatformPatterns.psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                    val test = PsiTreeUtil.collectElements(headerPsi, {
                        headerValuePartFinder.accepts(it) && it?.text?.trim()?.startsWith(container.bundleSymbolicName) ?: false
                    }).firstOrNull() ?: headerPsi

                    if (container.error != null) {
                        manager.createProblemDescriptor(test,
                                "Bundle ${container.bundleSymbolicName} not found",
                                false,
                                emptyArray(),
                                ProblemHighlightType.ERROR)
                    } else {
                        manager.createProblemDescriptor(test,
                                "Bundle ${container.bundleSymbolicName} is only available in the baseline repo and not part of the project anymore",
                                false,
                                emptyArray(),
                                ProblemHighlightType.ERROR)
                    }


                }
    }
}