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

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.components.service
import com.intellij.packageDependencies.ui.PackageDependenciesNode
import com.intellij.psi.PsiDirectory
import com.intellij.ui.ColoredTreeCellRenderer
import icons.OsmorcIdeaIcons

/**
 * Decorate packages in the ProjectView
 *
 *  - Add a '+' sign to packages that are exporte</li>
 *  - Add a '-' sign to private packages
 *  - Add a warning icon to packages that are not included in a bundle
 */
class AmdatuIdeaProjectViewNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>?, data: PresentationData?) {

        data ?: return
        if (node?.value !is PsiDirectory) return // (currently) only packages are annotated so it it's not a dir skip the node
        val psiDirectory = node.value as PsiDirectory
        val project = node.project ?: return

        val packageInfoService = project.service<PackageInfoService>() ?: return

        when (packageInfoService.packageStatus(psiDirectory)){
            PackageStatus.EXPORTED -> data.setIcon(OsmorcIdeaIcons.ExportedPackage)
            PackageStatus.PRIVATE -> data.setIcon(OsmorcIdeaIcons.PrivatePackage)
            PackageStatus.NOT_INCLUDED -> if (psiDirectory.children.firstOrNull { it !is PsiDirectory } != null) {
                // Only mark the package as not included if it contains files to prevent "empty middle packages"
                // from being annotated in the non flattened view
                data.setIcon(OsmorcIdeaIcons.NotIncludedPackage)
            }
        }
    }

    override fun decorate(node: PackageDependenciesNode?, cellRenderer: ColoredTreeCellRenderer?) {
        // n00p
    }

}