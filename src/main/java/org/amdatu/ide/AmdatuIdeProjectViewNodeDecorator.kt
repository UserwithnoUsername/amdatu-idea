package org.amdatu.ide

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
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
class AmdatuIdeProjectViewNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>?, data: PresentationData?) {

        data ?: return
        if (node?.value !is PsiDirectory) return // (currently) only packages are annotated so it it's not a dir skip the node
        val psiDirectory = node.value as PsiDirectory
        val project = node.project ?: return

        val amdatuIdePlugin = project.getComponent(AmdatuIdePlugin::class.java)

        val packageInfoService = amdatuIdePlugin.packageInfoSevice

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