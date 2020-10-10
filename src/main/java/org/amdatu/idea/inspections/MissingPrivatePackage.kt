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

import aQute.bnd.osgi.Constants
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.util.Computable
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.amdatu.idea.lang.bundledescriptor.psi.BundleDescriptorTokenType
import org.amdatu.idea.lang.bundledescriptor.psi.Header

class MissingPrivatePackage : LocalInspectionTool() {

    override fun getDisplayName(): String {
        return "Non existing private package"
    }

    override fun getStaticDescription(): String? {
        return """
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

        return getApplication().runReadAction(Computable {
            val privatePackages = PackageUtil.getModulePackageInfo(module, builder.privatePackage)

            val privatePackageHeader = psiElement(Header::class.java)
                    .withName(Constants.PRIVATE_PACKAGE, Constants.PRIVATEPACKAGE)
            val privatePackageHeaderPsi = PsiTreeUtil
                    .collectElements(file, privatePackageHeader::accepts)
                    .firstOrNull()
                    ?: file

            privatePackages
                    .filter { !it.exists }
                    .map { packageInfo ->

                        val headerValuePartFinder = psiElement(BundleDescriptorTokenType.HEADER_VALUE_PART)
                        val test = PsiTreeUtil.collectElements(privatePackageHeaderPsi) {
                            headerValuePartFinder.accepts(it) && it.text?.startsWith(packageInfo.fqn) ?: false
                        }.firstOrNull() ?: privatePackageHeaderPsi

                        manager.createProblemDescriptor(test,
                                "Package '${packageInfo.fqn}' doesn't exist.",
                                false,
                                emptyArray(),
                                ProblemHighlightType.ERROR)
                    }
                    .toTypedArray()
        })
    }
}

