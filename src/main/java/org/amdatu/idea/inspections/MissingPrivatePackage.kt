package org.amdatu.idea.inspections

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
import org.amdatu.idea.lang.bundledescriptor.psi.BundleDescriptorTokenType
import org.amdatu.idea.lang.bundledescriptor.psi.Header

class MissingPrivatePackage : LocalInspectionTool() {

    override fun getDisplayName(): String {
        return "Non existing private package"
    }

    override fun getStaticDescription(): String? {
        return  """
            This inspection checks whether a private package exists in the module sources or in the module build path.
        """.trimIndent()
    }

    override fun getGroupDisplayName(): String {
        // TODO: Inspection group name should be a const
        return "Amdatu"
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!file.name.endsWith(".bnd")) {
            return null
        }

        val (_, module, _, _, builder) =
                PsiUtil.getBndBuilderContextForPsiFile(file) ?: return null

        return getApplication().runReadAction(Computable<Array<ProblemDescriptor>> {
            val array = arrayListOf<ProblemDescriptor>()

            val privatePackages = PackageUtil.getModulePackageInfo(module, builder.privatePackage)

            val privatePackageHeader = psiElement<Header>(Header::class.java)
                    .withName(Constants.PRIVATE_PACKAGE, Constants.PRIVATEPACKAGE)
            val privatePackageHeaderPsi = PsiTreeUtil
                    .collectElements(file, privatePackageHeader::accepts)
                    .firstOrNull()
                    ?: file

            array.addAll(privatePackages
                    .filter { !it.exists }
                    .map { packageInfo ->

                        val headerValuePartFinder = psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                        val test = PsiTreeUtil.collectElements(privatePackageHeaderPsi, {
                            headerValuePartFinder.accepts(it) && it?.text?.startsWith(packageInfo.fqn) ?: false
                        }).firstOrNull() ?: privatePackageHeaderPsi

                        manager.createProblemDescriptor(test,
                                "Package '${packageInfo.fqn}' doesn't exist.",
                                false,
                                emptyArray(),
                                ProblemHighlightType.ERROR)
                    })

            return@Computable array.toTypedArray()
        })
    }
}

