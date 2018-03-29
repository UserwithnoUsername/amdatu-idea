package org.amdatu.ide

import aQute.bnd.osgi.Instructions
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.roots.ProjectFileIndex
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
        val file = node.virtualFile ?: return


        val projectFileIndex = ProjectFileIndex.getInstance(project)


        if (file == projectFileIndex.getSourceRootForFile(file)) return // Don't process source roots
        if (projectFileIndex.isInTestSourceContent(file)) return // Don't annotate test packages

        val packageName = projectFileIndex.getPackageNameByDirectory(file) ?: return // Only do packages
        val module = projectFileIndex.getModuleForFile(file) ?: return

        val amdatuIdePlugin = project.getComponent(AmdatuIdePlugin::class.java)
        if (amdatuIdePlugin?.isWorkspaceInitialized == false) return
        val workspace = amdatuIdePlugin.workspace ?: return

        var isPrivate = false
        var isExported = false
        val bndProject = workspace.getProject(module.name)

        bndProject
                ?.getBuilder(null)
                ?.subBuilders
                ?.forEach { builder ->
                    if (!isExported) isExported = builder.exportPackage.isNotEmpty() && Instructions(builder.exportPackage).matches(packageName)
                    if (!isPrivate) isPrivate = builder.privatePackage.isNotEmpty() && Instructions(builder.privatePackage).matches(packageName)
                }

        when {
            isExported -> data.setIcon(OsmorcIdeaIcons.ExportedPackage)
            isPrivate -> data.setIcon(OsmorcIdeaIcons.PrivatePackage)
            // Only mark the package as not included if it contains files to prevent "empty middle packages" from being annotated in the non flattened view
            psiDirectory.children.firstOrNull { it !is PsiDirectory } != null -> data.setIcon(OsmorcIdeaIcons.NotIncludedPackage)
        }

    }

    override fun decorate(node: PackageDependenciesNode?, cellRenderer: ColoredTreeCellRenderer?) {
        // n00p
    }

}