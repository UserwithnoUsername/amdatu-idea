package org.amdatu.ide.inspections

import aQute.bnd.osgi.Constants
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.util.Computable
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.amdatu.ide.lang.bundledescriptor.psi.BundleDescriptorTokenType
import org.amdatu.ide.lang.bundledescriptor.psi.Header

class ExportedPackageWithoutVersion : LocalInspectionTool() {

    override fun getDisplayName(): String {
        return "Exported package without version"
    }

    override fun getStaticDescription(): String? {
        return """
            This inspection checks whether an exported package has a version specified.

            Version can be specified in:
             - package-info.java (@Version annotation)
             - packageinfo file
             - version attribute (Export-Package: x.y.z;version=1.2.3)
        """.trimIndent()
    }

    override fun getGroupDisplayName(): String {
        return "Amdatu Ide"
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!file.name.endsWith(".bnd")) {
            return null
        }

        val (_, module, _, _, builder) =
                PsiUtil.getBndBuilderContextForPsiFile(file) ?: return null

        return getApplication().runReadAction(Computable<Array<ProblemDescriptor>> {
            val array = arrayListOf<ProblemDescriptor>()

            val exportPackageHeader = psiElement<Header>(Header::class.java).withName(Constants.EXPORT_PACKAGE)
            val exportPackageHeaderPsi = PsiTreeUtil.collectElements(file, exportPackageHeader::accepts).firstOrNull()
                    ?: file
            val packages = PackageUtil.getModulePackageInfo(module, builder.exportPackage)

            array.addAll(packages
                    .filter { it.version == null && it.partOfModuleSource }
                    .map { packageInfo ->

                        val headerValuePartFinder = psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                        val test = PsiTreeUtil.collectElements(exportPackageHeaderPsi, {
                            headerValuePartFinder.accepts(it) && it?.text?.startsWith(packageInfo.fqn) ?: false
                        }).firstOrNull() ?: exportPackageHeaderPsi

                        manager.createProblemDescriptor(test,
                                "Package '${packageInfo.fqn}' has no version specified",
                                false,
                                emptyArray(),
                                ProblemHighlightType.ERROR)
                    })


            return@Computable array.toTypedArray()
        })
    }
}

