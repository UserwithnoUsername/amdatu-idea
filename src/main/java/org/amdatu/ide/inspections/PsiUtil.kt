package org.amdatu.ide.inspections

import aQute.bnd.build.Project
import aQute.bnd.build.Workspace
import aQute.bnd.osgi.Builder
import aQute.bnd.osgi.Constants
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiFile
import org.amdatu.ide.AmdatuIdePlugin
import java.io.File

data class BndBuilderContext(
        val psiFile: PsiFile,
        val module: Module,
        val workspace: Workspace,
        val bndProject: Project,
        val builder: Builder
)

class PsiUtil {

    companion object {
        private val logger = Logger.getInstance(PsiUtil::class.java)

        fun getBndBuilderContextForPsiFile(psiFile: PsiFile): BndBuilderContext? {
            val module = getModuleForPsiFile(psiFile) ?: return null
            val workspace = getBndWorkspace(psiFile) ?: return null

            val bndProject = getBndProject(workspace, module) ?: return null

            val builder: Builder = (if (bndProject.get(Constants.SUB) == null || psiFile.name == "bnd.bnd") {
                bndProject.getBuilder(null)
            } else {
                bndProject.getSubBuilder(File(psiFile.virtualFile.path))
            }) ?: return null

            return BndBuilderContext(psiFile, module, workspace, bndProject, builder)
        }

        private fun getBndProject(workspace: Workspace, module: Module): Project? {
            val project = workspace.getProject(module.name)
            if (project == null) {
                logger.debug("Failed to get bnd project for Module ${module.name}")
            }
            return project
        }

        private fun getBndWorkspace(psiFile: PsiFile): Workspace? {
            val amdatuIdePlugin = getAmdatuIdePlugin(psiFile) ?: return null
            val workspace = amdatuIdePlugin.workspace
            if (workspace == null) {
                logger.debug({ "Failed to get module for PsiFile: $psiFile" })
            }
            return workspace
        }

        private fun getModuleForPsiFile(psiFile: PsiFile): Module? {
            val module = ProjectFileIndex.getInstance(psiFile.project).getModuleForFile(psiFile.virtualFile)
            if (module == null) {
                logger.debug({ "Failed to get module for PsiFile: $psiFile" })
            }
            return module
        }

        private fun getAmdatuIdePlugin(psiFile: PsiFile): AmdatuIdePlugin? {
            val amdatuIdePlugin = psiFile.project.getComponent(AmdatuIdePlugin::class.java)

            if (amdatuIdePlugin == null) {
                logger.debug({ "AmdatuIdePlugin component not available for project: ${psiFile.project}" })
            }

            return amdatuIdePlugin
        }

        private inline fun Logger.debug(lazyMessage: () -> String) {
            if (isDebugEnabled) {
                debug(lazyMessage())
            }
        }
    }
}