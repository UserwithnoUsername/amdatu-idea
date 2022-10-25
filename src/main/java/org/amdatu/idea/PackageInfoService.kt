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

package org.amdatu.idea

import aQute.bnd.osgi.Instructions
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.search.GlobalSearchScope
import org.amdatu.idea.inspections.PackageUtil

enum class PackageStatus { EXPORTED, PRIVATE, NOT_INCLUDED }

data class PackageInfo(val qualifiedName: String, val state: PackageStatus)

@Service
class PackageInfoService(private val myProject: Project) {

    private val myPackageStatusMap: MutableMap<PsiDirectory, PackageInfo> = mutableMapOf()

    init {
        object : Task.Backgroundable(myProject, "Updating package info", false) {
            override fun run(progressIndicator: ProgressIndicator) {
                ApplicationManager.getApplication().runReadAction {
                    updatePackageStateMap()
                }
            }
        }.queue()


        val connection = myProject.messageBus.connect()
        connection.subscribe(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED,
            WorkspaceRefreshedNotifier {
                ProgressManager.getInstance().runProcess({
                    ApplicationManager.getApplication().runReadAction {
                        updatePackageStateMap()
                    }
                }, null)
            })
    }

    fun packageStatus(psiDirectory: PsiDirectory): PackageStatus? {
        return myPackageStatusMap[psiDirectory]?.state
    }

    private fun updatePackageStateMap() {
        try {
            val packageStatusMap: MutableMap<PsiDirectory, PackageInfo> = mutableMapOf()

            val instance = ModuleManager.getInstance(myProject)
            for (module in instance.modules) {
                val psiPackagesForModule = PackageUtil.getPsiPackagesForModule(module)
                if (psiPackagesForModule.isEmpty()) {
                    // Don't even bother looking for a bnd project
                    continue
                }

                val bndProject = myProject.service<AmdatuIdeaPlugin>()
                    .withWorkspace { workspace -> workspace.getProject(module.name) } ?: continue

                val exportPackageInstructions = Instructions()
                val privatePackageInstructions = Instructions()

                for (builder in bndProject.getBuilder(null).subBuilders) {
                    val exportPackage = builder.exportPackage
                    if (exportPackage !== null) {
                        exportPackageInstructions.append(exportPackage)
                    }

                    val privatePackage = builder.privatePackage
                    if (privatePackage !== null) {
                        privatePackageInstructions.append(privatePackage)
                    }
                }

                for (psiPackage in psiPackagesForModule) {
                    val moduleSourceScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false)
                    val directories = psiPackage.getDirectories(moduleSourceScope)

                    if (directories.size == 1) {
                        val exportedPackage = !exportPackageInstructions.isEmpty() && exportPackageInstructions
                            .matches(psiPackage.qualifiedName)
                        val privatePackage = !privatePackageInstructions.isEmpty() && privatePackageInstructions
                            .matches(psiPackage.qualifiedName)


                        val status = when {
                            exportedPackage -> PackageStatus.EXPORTED
                            privatePackage -> PackageStatus.PRIVATE
                            else -> PackageStatus.NOT_INCLUDED

                        }

                        packageStatusMap[directories[0]] = PackageInfo(psiPackage.qualifiedName, status)
                    }
                }
            }
            myPackageStatusMap.apply {
                putAll(packageStatusMap)
                keys.retainAll(packageStatusMap.keys)
            }


            ProjectView.getInstance(myProject).refresh()
        } catch (e: Exception) {
//            LOG.error("Failed to refresh package state map", e)
        }
    }
}