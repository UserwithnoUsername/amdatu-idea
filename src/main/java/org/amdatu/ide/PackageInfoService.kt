package org.amdatu.ide

import aQute.bnd.osgi.Instructions
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.search.GlobalSearchScope
import org.amdatu.ide.inspections.PackageUtil

enum class PackageStatus { EXPORTED, PRIVATE, NOT_INCLUDED }

data class PackageInfo(val qualifiedName: String, val state: PackageStatus)

class PackageInfoService(project: Project, amdatuIdePlugin: AmdatuIdePlugin) {

    private val myProject: Project = project
    private val myAmdatuIdePlugin: AmdatuIdePlugin = amdatuIdePlugin
    private val myPackageStatusMap: MutableMap<PsiDirectory, PackageInfo> = mutableMapOf()

    init {
        updatePackageStateMap()

        val connection = myProject.messageBus.connect()
        connection.subscribe(WorkspaceRefreshedNotifier.WORKSPACE_REFRESHED,
                WorkspaceRefreshedNotifier {
                    ProgressManager.getInstance().runProcess({
                        updatePackageStateMap()
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

                val bndProject = myAmdatuIdePlugin.workspace.getProject(module.name) ?: continue

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