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

package org.amdatu.idea.lang.bundledescriptor.completion

import aQute.bnd.osgi.Constants
import aQute.bnd.service.RepositoryPlugin
import com.intellij.openapi.project.Project
import org.amdatu.idea.AmdatuIdeaPlugin

class RepoUtil {

    companion object {

        fun getBundles(project: Project): Set<String> {
            val amdatuIdePlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return emptySet()

            val repositories = amdatuIdePlugin.workspace?.getPlugins(RepositoryPlugin::class.java) ?: return emptySet()

            return repositories
                    .flatMap { it.list(null) }
                    .toSortedSet()

        }

        fun getBundlesOnlyAvailableInBaselineRepo(project: Project): Set<String> {
            val amdatuIdePlugin = project.getComponent(AmdatuIdeaPlugin::class.java) ?: return emptySet()

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