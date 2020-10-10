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

import aQute.bnd.build.Project
import aQute.bnd.build.Workspace
import aQute.bnd.osgi.Builder
import aQute.bnd.osgi.Constants
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiFile
import org.amdatu.idea.AmdatuIdeaPlugin
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
            return getAmdatuIdePlugin(psiFile)?.withWorkspace { workspace ->

                val bndProject = getBndProject(workspace, module) ?: return@withWorkspace null

                val builder: Builder = (if (bndProject.get(Constants.SUB) == null || psiFile.name == "bnd.bnd") {
                    bndProject.getBuilder(null)
                } else {
                    bndProject.getSubBuilder(File(psiFile.virtualFile.path))
                }) ?: return@withWorkspace null

                BndBuilderContext(psiFile, module, workspace, bndProject, builder)
            }
        }

        private fun getBndProject(workspace: Workspace, module: Module): Project? {
            val project = workspace.getProject(module.name)
            if (project == null) {
                logger.debug("Failed to get bnd project for Module ${module.name}")
            }
            return project
        }


        private fun getModuleForPsiFile(psiFile: PsiFile): Module? {
            val module = ProjectFileIndex.getInstance(psiFile.project).getModuleForFile(psiFile.virtualFile)
            if (module == null) {
                logger.debug { "Failed to get module for PsiFile: $psiFile" }
            }
            return module
        }

        private fun getAmdatuIdePlugin(psiFile: PsiFile): AmdatuIdeaPlugin? {
            val amdatuIdePlugin = psiFile.project.getComponent(AmdatuIdeaPlugin::class.java)

            if (amdatuIdePlugin == null) {
                logger.debug { "AmdatuIdeaPlugin component not available for project: ${psiFile.project}" }
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
