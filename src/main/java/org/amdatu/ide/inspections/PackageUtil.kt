package org.amdatu.ide.inspections

import aQute.bnd.header.Parameters
import aQute.bnd.osgi.Instructions
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.module.Module
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import com.intellij.psi.search.GlobalSearchScope
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream

data class PackageInfo(
        val fqn: String,
        val version: String?,
        val exists: Boolean,
        val partOfModuleSource: Boolean
)

class PackageUtil {

    companion object {

        /**
         * Get information about packages from PSI model for packages in a bnd Parameters object
         *
         * @param module The module
         * @param parameters Bnd parameters with package information (private / export package list)
         */
        fun getModulePackageInfo(module: Module, parameters: Parameters): List<PackageInfo> {
            val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
            val javaPsiFacade = JavaPsiFacade.getInstance(module.project)

            val result: MutableList<PackageInfo> = mutableListOf()

            val packagesForModule = getPsiPackagesForModule(module).map { it.qualifiedName }

            for ((instruction, attrs) in Instructions(parameters)) {
                val pkg = when {
                    instruction.isLiteral -> instruction.input
                    instruction.input.endsWith(".*") -> instruction.input.substring(0, instruction.input.length - 2)
                    instruction.input.endsWith("*") -> instruction.input.substring(0, instruction.input.length - 1)
                    else -> instruction.input
                }

                val rootPackage = javaPsiFacade.findPackage(pkg)
                if (rootPackage == null) {
                    println("Package not found: $pkg")
                    result.add(PackageInfo(pkg, null, false, false))
                    continue
                }

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
                        attrs["version"] != null -> attrs["version"]
                        version is PsiLiteralExpression -> version.text.replace("\"", "")
                        version is PsiReferenceExpression -> ((version as PsiReferenceExpressionImpl).resolve() as PsiField).initializer!!.text
                        else -> {
                            it.getFiles(scope)
                                    .filter { it.name == "packageinfo" }
                                    .map {
                                        val toString = IOUtils.toString(FileInputStream(File(it.virtualFile.path)))
                                        val matchResult = "version (.*)".toRegex().find(toString)

                                        matchResult?.groupValues?.get(1)
                                    }
                                    .firstOrNull()
                        }
                    }

                    val packageInfo = PackageInfo(it.qualifiedName, strVersion, true, packagesForModule.contains(it.qualifiedName))
                    println(packageInfo)
                    result += packageInfo
                }
            }
            return result.toList()
        }

        private fun getSubPackages(psiPackage: PsiPackage, globalSearchScope: GlobalSearchScope): List<PsiPackage> {
            val subPackages = psiPackage.getSubPackages(globalSearchScope)
            val list = mutableListOf(psiPackage)
            if (!subPackages.isEmpty()) {
                list.addAll(subPackages.flatMap { getSubPackages(it, globalSearchScope) })
            }
            return list
        }

        /**
         * Get source packages for a module
         *
         * @param module the module to get a list of packages for
         */
        fun getPsiPackagesForModule(module: Module): Set<PsiPackage> {

            val packages = mutableSetOf<PsiPackage>()

            val moduleScope = AnalysisScope(module)
            val javaPsiFacade = JavaPsiFacade.getInstance(module.project)

            moduleScope.accept(object : PsiRecursiveElementVisitor() {
                override fun visitFile(file: PsiFile) {
                    if (file is PsiJavaFile) {
                        val psiPackage = javaPsiFacade.findPackage(file.packageName)
                        if (psiPackage != null) {
                            packages.add(psiPackage)
                        }
                    }
                }
            })
            return packages
        }
    }
}

