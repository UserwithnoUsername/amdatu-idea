package org.amdatu.ide.lang.bundledescriptor.completion

import aQute.bnd.osgi.Constants
import aQute.bnd.service.RepositoryPlugin
import com.intellij.openapi.project.Project
import org.amdatu.ide.AmdatuIdePlugin

class RepoUtil {

    companion object {

        fun getBundles(project: Project): Set<String> {
            val amdatuIdePlugin = project.getComponent(AmdatuIdePlugin::class.java) ?: return emptySet()

            val repositories = amdatuIdePlugin.workspace?.getPlugins(RepositoryPlugin::class.java) ?: return emptySet()

            return repositories
                    .flatMap { it.list(null) }
                    .toSortedSet()

        }

        fun getBundlesOnlyAvailableInBaselineRepo(project: Project): Set<String> {
            val amdatuIdePlugin = project.getComponent(AmdatuIdePlugin::class.java) ?: return emptySet()

            val workspace = amdatuIdePlugin.workspace ?: return emptySet()

            val baselineRepoName = workspace.get(Constants.BASELINEREPO) ?: return emptySet() // no baseline repo

            val repositories = workspace.getPlugins(RepositoryPlugin::class.java) ?: return emptySet()

            return repositories
                    .filter { it.name == baselineRepoName }
                    .flatMap { it.list(null) }
                    .filter { workspace.getProject(it) == null }
                    .toSet()
        }

    }

}