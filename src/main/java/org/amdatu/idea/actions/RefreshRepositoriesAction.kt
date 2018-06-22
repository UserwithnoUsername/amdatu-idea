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

package org.amdatu.idea.actions

import aQute.bnd.repository.maven.provider.MavenBndRepository
import aQute.bnd.service.Refreshable
import aQute.bnd.service.RepositoryPlugin
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.amdatu.idea.AmdatuIdeaPlugin

class RefreshRepositoriesAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val amdatuIdeaPlugin = event.project?.getComponent(AmdatuIdeaPlugin::class.java) ?: return
        val workspace = amdatuIdeaPlugin.workspace ?: return
        val plugins: List<RepositoryPlugin> = workspace.getPlugins(RepositoryPlugin::class.java)

        plugins.filter { it is Refreshable }
                .forEach { plugin ->
                    try {
                        val refreshablePlugin = plugin as Refreshable
                        refreshablePlugin.refresh()
                    } catch (e: Exception) {
                        if (plugin is MavenBndRepository && e is NullPointerException) {
                            // This repo doesn't init until it's used and throws an NPE on refresh
                            // TODO: Report as BND issue (if not already fixed in next)
                            LOG.info("Failed to refresh repository, '${plugin.name}'", e)
                        } else {
                            LOG.error("Failed to refresh repository, '${plugin.name}'", e)
                        }
                    }
                }
        amdatuIdeaPlugin.refreshWorkspace(true)
    }


}