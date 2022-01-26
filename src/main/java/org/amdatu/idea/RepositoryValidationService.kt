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

import aQute.bnd.build.Workspace
import aQute.bnd.service.RepositoryPlugin
import com.intellij.openapi.project.Project


class RepositoryValidator(val project: Project, val amdatuIdeaPlugin: AmdatuIdeaPlugin) {

    /**
     * Validate uri's used in as locations for OSGiRepository and FixedIndexedRepo instances in the workspace and report
     * issues.
     *
     * @param workspace Amdatu idea plugin instance
     */
     fun validateRepositories(workspace: Workspace, ): Boolean {
        val osgiReposOk: Boolean = workspace.getPlugins(RepositoryPlugin::class.java)
                .map { repositoryPlugin ->
                    val status = repositoryPlugin.status
                    if (status != null) {
                        amdatuIdeaPlugin.warning("""
                                Repository has errors: '${repositoryPlugin.name}'.
                                Status: $status
                                """.trimIndent())
                         false
                    } else {
                        true
                    }
                }
            .all { it }

        return osgiReposOk
    }

}
