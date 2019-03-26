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

import aQute.bnd.osgi.Constants
import aQute.bnd.service.RepositoryPlugin
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

val LOG = Logger.getInstance("org.amdatu.idea.RepoUtil")

fun getBundles(project: Project): Set<String> {
    val amdatuIdePlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return emptySet()

    return amdatuIdePlugin
            .withWorkspace { workspace -> workspace.getPlugins(RepositoryPlugin::class.java) }
            .flatMap { it.list(null) }
            .toSortedSet()

}

fun getBundlesOnlyAvailableInBaselineRepo(project: Project): Set<String> {
    val amdatuIdePlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return emptySet()

    return amdatuIdePlugin.withWorkspace { workspace ->
        val baselineRepoName = workspace.get(Constants.BASELINEREPO)

        if (baselineRepoName == null) {
            emptySet()
        } else {
            workspace
                    .getPlugins(RepositoryPlugin::class.java)
                    .filter { repositoryPlugin -> repositoryPlugin.name == baselineRepoName }
                    .flatMap { repositoryPlugin -> repositoryPlugin.list(null) }
                    .filter { bsn -> workspace.getProject(bsn) == null }
                    .toSet()

        }
    }
}
