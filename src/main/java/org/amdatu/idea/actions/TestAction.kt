package org.amdatu.idea.actions

import aQute.bnd.osgi.Instructions
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem.getInstance
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.impl.JavaPsiFacadeImpl
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import com.intellij.psi.search.GlobalSearchScope
import org.amdatu.idea.AmdatuIdeaPlugin
import java.io.File

class TestAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val javaPsiFacade = JavaPsiFacade.getInstance(e.project) as? JavaPsiFacadeImpl ?: return

        val workspace = e.project!!.getComponent(AmdatuIdeaPlugin::class.java)!!.workspace!!

        val project = workspace.getProject("org.amdatu.web.rest")

        project.getBuilder(null).subBuilders.forEach({ builder ->
            for ((instruction, attrs) in Instructions(builder.exportPackage)) {
                val pkg = when {
                    instruction.isLiteral -> instruction.input
                    instruction.input.endsWith(".*") -> instruction.input.substring(0, instruction.input.length - 2)
                    instruction.input.endsWith("*") -> instruction.input.substring(0, instruction.input.length - 1)
                    else -> instruction.input
                }
                VirtualFileManager.getInstance().findFileByUrl(builder.propertiesFile.absolutePath)


                val rootPackage = javaPsiFacade.findPackage(pkg)
                if (rootPackage == null) {
                    println("Package not found: $pkg")
                    continue
                }

                val file = getInstance().findFileByIoFile(File(builder.propertiesFile.absolutePath))
                val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(moduleForFile(e.project!!, file!!))

                val packages: List<PsiPackage> = if (!instruction.isLiteral) {
                    getSubPackages(rootPackage, scope)
                } else {
                    listOf(rootPackage)
                }

                packages.forEach {

                    val version = it.annotations
                            .firstOrNull { it.qualifiedName == "org.osgi.annotation.versioning.Version" }
                            ?.findAttributeValue(null)

                    val strVersion = when {
                        version is PsiLiteralExpression -> version.text
                        version is PsiReferenceExpression -> ((version as PsiReferenceExpressionImpl).resolve() as PsiField).initializer!!.text
                        attrs["version"] != null -> attrs["version"]
                        // TODO: look for packageinfo text file in package
                        // TODO: Ignore getModulePackageInfo without version
                        else -> "oops"
                    }


                    println("PKG:  ${it.qualifiedName} matches: ${instruction.matches(it.qualifiedName)} version: $strVersion")
                }
            }
        })
    }

    private fun moduleForFile(project: Project, file: VirtualFile) =
            ProjectFileIndex.getInstance(project).getModuleForFile(file)!!

    private fun getSubPackages(psiPackage: PsiPackage, globalSearchScope: GlobalSearchScope): List<PsiPackage> {
        val subPackages = psiPackage.getSubPackages(globalSearchScope)
        val list = mutableListOf(psiPackage)
        if (!subPackages.isEmpty()) {
            list.addAll(subPackages.flatMap { getSubPackages(it, globalSearchScope) })
        }
        return list
    }

}